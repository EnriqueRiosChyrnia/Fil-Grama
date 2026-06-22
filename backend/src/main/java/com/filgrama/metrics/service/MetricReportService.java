package com.filgrama.metrics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Metric;
import com.filgrama.domain.Post;
import com.filgrama.domain.SocialAccount;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.MetricFormat;
import com.filgrama.metrics.dto.AccountReportRequest;
import com.filgrama.metrics.dto.AccountReportResponse;
import com.filgrama.metrics.dto.BatchReportRequest;
import com.filgrama.metrics.dto.BatchReportResponse;
import com.filgrama.metrics.dto.DateRange;
import com.filgrama.metrics.dto.MetricSeries;
import com.filgrama.metrics.dto.MetricsReportRequest;
import com.filgrama.metrics.dto.PostReportResponse;
import com.filgrama.metrics.dto.SeriesPoint;
import com.filgrama.metrics.repository.MetricsQueryRepository;
import com.filgrama.metrics.repository.MetricsQueryRepository.SeriesRow;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Informes de series temporales (patrón GA4 {@code runReport}/{@code batchRunReports}): N métricas +
 * rango en una sola request, una serie por métrica lista para graficar. Sin legacy (no existe el
 * patrón "una métrica por request").
 *
 * <p><b>Performance:</b> UNA query SQL por cuenta/post ({@code metric_key IN (:metrics)} + filtro de
 * fechas y de {@code client_id} en SQL); el agrupado por métrica es en memoria sobre el resultado
 * ya filtrado. Nada de cargar todos los snapshots y filtrar con streams.
 *
 * <p><b>Multi-tenant:</b> se resuelve el {@code client_id} del recurso (cuenta/post) y la query filtra
 * por él, así una cuenta nunca ve snapshots de otro cliente. En batch cada request se resuelve de
 * forma independiente.
 *
 * <p><b>Batch + cuenta inexistente:</b> el batch es <b>atómico</b> — un {@code accountId} inexistente
 * hace fallar TODA la llamada con {@code 404} (espeja {@code batchRunReports} de GA4 y mantiene la
 * response con un shape estricto, sin marcadores de error que ensucien el cliente generado del front).
 */
@Service
public class MetricReportService {

    private static final String DEFAULT_GRANULARITY = "day";
    /** Default de rango: últimos 90 días (hoy-89 .. hoy), ambos inclusive. */
    private static final int DEFAULT_RANGE_DAYS = 90;
    private static final int MAX_BATCH_REQUESTS = 20;

    private final MetricCatalogService catalog;
    private final SocialAccountRepository accountRepository;
    private final PostRepository postRepository;
    private final MetricsQueryRepository queryRepository;
    private final Clock clock;

    public MetricReportService(MetricCatalogService catalog,
                               SocialAccountRepository accountRepository,
                               PostRepository postRepository,
                               MetricsQueryRepository queryRepository,
                               Clock clock) {
        this.catalog = catalog;
        this.accountRepository = accountRepository;
        this.postRepository = postRepository;
        this.queryRepository = queryRepository;
        this.clock = clock;
    }

    /** Informe de series de una cuenta: N métricas en una request. */
    @Transactional(readOnly = true)
    public AccountReportResponse accountReport(Long accountId, MetricsReportRequest request) {
        LinkedHashMap<String, Metric> metrics = catalog.requireReportMetrics(metricsOf(request));
        String granularity = resolveGranularity(granularityOf(request));
        DateRange range = resolveRange(dateRangeOf(request));

        SocialAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account %d not found".formatted(accountId)));
        Long clientId = account.getClientId();

        List<SeriesRow> rows = queryRepository.accountMetricSeries(
                clientId, accountId, metrics.keySet(), range.from(), range.to());
        return new AccountReportResponse(accountId, range, granularity, toSeries(metrics, rows));
    }

    /** Informe de series de un post: mismo shape que el de cuenta, con {@code postId}. */
    @Transactional(readOnly = true)
    public PostReportResponse postReport(Long postId, MetricsReportRequest request) {
        LinkedHashMap<String, Metric> metrics = catalog.requireReportMetrics(metricsOf(request));
        String granularity = resolveGranularity(granularityOf(request));
        DateRange range = resolveRange(dateRangeOf(request));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post %d not found".formatted(postId)));
        Long clientId = post.getClientId();

        List<SeriesRow> rows = queryRepository.postMetricSeries(
                clientId, postId, metrics.keySet(), range.from(), range.to());
        return new PostReportResponse(postId, range, granularity, toSeries(metrics, rows));
    }

    /**
     * Batch de informes en una sola llamada. Máx. {@value #MAX_BATCH_REQUESTS} requests ({@code 400}
     * si se excede). La response mantiene el MISMO orden que {@code requests}. Atómico: cualquier
     * request inválido (cuenta inexistente → 404, métrica inválida → 400) aborta todo el batch.
     */
    @Transactional(readOnly = true)
    public BatchReportResponse batchReport(BatchReportRequest request) {
        List<AccountReportRequest> requests = request == null ? null : request.requests();
        if (requests == null || requests.isEmpty()) {
            throw ApiException.badRequest("'requests' es requerido (1..%d informes)".formatted(MAX_BATCH_REQUESTS));
        }
        if (requests.size() > MAX_BATCH_REQUESTS) {
            throw ApiException.badRequest(
                    "el batch excede el máximo de %d requests (recibidos %d)"
                            .formatted(MAX_BATCH_REQUESTS, requests.size()));
        }

        List<AccountReportResponse> reports = new ArrayList<>(requests.size());
        for (AccountReportRequest r : requests) {
            if (r == null || r.accountId() == null) {
                throw ApiException.badRequest("cada request del batch requiere 'accountId'");
            }
            reports.add(accountReport(r.accountId(),
                    new MetricsReportRequest(r.metrics(), r.dateRange(), r.granularity())));
        }
        return new BatchReportResponse(reports);
    }

    // ---- helpers ----

    private static List<String> metricsOf(MetricsReportRequest r) {
        return r == null ? null : r.metrics();
    }

    private static String granularityOf(MetricsReportRequest r) {
        return r == null ? null : r.granularity();
    }

    private static DateRange dateRangeOf(MetricsReportRequest r) {
        return r == null ? null : r.dateRange();
    }

    /**
     * Agrupa las filas crudas en una serie por métrica, en el ORDEN pedido. Una métrica sin filas
     * queda con {@code points} vacío (rango sin datos → NO error). La {@code unit} sale del catálogo.
     */
    private static List<MetricSeries> toSeries(LinkedHashMap<String, Metric> metrics, List<SeriesRow> rows) {
        Map<String, List<SeriesPoint>> pointsByMetric = new LinkedHashMap<>();
        for (String key : metrics.keySet()) {
            pointsByMetric.put(key, new ArrayList<>());
        }
        for (SeriesRow row : rows) {
            // La query sólo trae las métricas pedidas; el get nunca es null, pero somos defensivos.
            List<SeriesPoint> bucket = pointsByMetric.get(row.metricKey());
            if (bucket != null) {
                bucket.add(new SeriesPoint(row.date(), MetricFormat.clean(row.value())));
            }
        }
        List<MetricSeries> series = new ArrayList<>(metrics.size());
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            series.add(new MetricSeries(entry.getKey(), entry.getValue().getUnit(),
                    pointsByMetric.get(entry.getKey())));
        }
        return series;
    }

    /** v1 sólo soporta {@code day}; {@code week}/{@code month} están reservados → cualquier otro valor es 400. */
    private static String resolveGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return DEFAULT_GRANULARITY;
        }
        String value = granularity.trim().toLowerCase(Locale.ROOT);
        if (!value.equals(DEFAULT_GRANULARITY)) {
            throw ApiException.badRequest(
                    "granularity '%s' no soportado en v1 (sólo 'day')".formatted(granularity));
        }
        return value;
    }

    /**
     * Resuelve el rango a fechas concretas. Sin {@code dateRange} → últimos 90 días (hoy-89 .. hoy).
     * Con un solo límite, el otro se completa (default 90 días). {@code from > to} → {@code 400}.
     */
    private DateRange resolveRange(DateRange requested) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = requested == null ? null : requested.from();
        LocalDate to = requested == null ? null : requested.to();

        if (to == null) {
            to = today;
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_RANGE_DAYS - 1L);
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
        }
        return new DateRange(from, to);
    }
}
