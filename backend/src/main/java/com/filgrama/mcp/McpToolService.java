package com.filgrama.mcp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Client;
import com.filgrama.error.ApiException;
import com.filgrama.mcp.McpPeriods.Range;
import com.filgrama.mcp.dto.ClientView;
import com.filgrama.mcp.dto.CompareView;
import com.filgrama.mcp.dto.CompareView.MetricCompare;
import com.filgrama.mcp.dto.CompareView.PeriodRef;
import com.filgrama.mcp.dto.CompareView.PlatformCompare;
import com.filgrama.mcp.dto.DemographicsView;
import com.filgrama.mcp.dto.DemographicsView.PlatformDemographics;
import com.filgrama.mcp.dto.NarrativeSaved;
import com.filgrama.mcp.dto.PostingPerformanceView;
import com.filgrama.mcp.dto.PostingPerformanceView.Bucket;
import com.filgrama.mcp.dto.ReportView;
import com.filgrama.mcp.dto.ReportView.PostView;
import com.filgrama.metrics.dto.AccountReportResponse;
import com.filgrama.metrics.dto.DateRange;
import com.filgrama.metrics.dto.MetricsReportRequest;
import com.filgrama.metrics.service.MetricReportService;
import com.filgrama.reports.ReportNarrativeService;
import com.filgrama.reports.ReportNarrativeService.SaveResult;
import com.filgrama.reports.ReportQueryRepository;
import com.filgrama.reports.ReportQueryRepository.PostPerfRow;
import com.filgrama.reports.ReportService;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.PlatformKpis;

/**
 * Lógica de las 7 tools MCP (spec/08). <b>No duplica queries</b>: reusa el {@link ReportService}
 * (mismo armado que {@code :preview}/export), el {@link MetricReportService} (series de cuenta) y
 * {@link ReportQueryRepository}. El scope se valida SIEMPRE en el backend vía {@link ClientAccessService}
 * antes de tocar datos (spec/11): un empleado nunca ve un cliente/cuenta ajeno. Solo
 * {@link #saveReportNarrative} escribe; el resto es de lectura.
 */
@Service
public class McpToolService {

    /** Claves de "alcance/visualizaciones" por post, por red (para el análisis de horarios). */
    private static final List<String> REACH_KEYS = List.of("ig_post_reach", "fb_post_views", "tt_view_count");

    /** Claves de "interacción" por post a sumar (cada post solo tiene las de su red). */
    private static final List<String> ENGAGEMENT_KEYS = List.of(
            "ig_post_total_interactions", "fb_post_reactions_total",
            "tt_like_count", "tt_comment_count", "tt_share_count");

    private static final Locale ES = Locale.forLanguageTag("es");

    private final ClientAccessService access;
    private final ReportService reportService;
    private final MetricReportService metricReportService;
    private final ReportQueryRepository reportQuery;
    private final ReportNarrativeService narrativeService;

    public McpToolService(ClientAccessService access,
                          ReportService reportService,
                          MetricReportService metricReportService,
                          ReportQueryRepository reportQuery,
                          ReportNarrativeService narrativeService) {
        this.access = access;
        this.reportService = reportService;
        this.metricReportService = metricReportService;
        this.reportQuery = reportQuery;
        this.narrativeService = narrativeService;
    }

    // ============================ list_clients ============================

    @Transactional(readOnly = true)
    public List<ClientView> listClients(McpIdentity identity) {
        return access.listAccessible(identity).stream()
                .map(c -> new ClientView(c.getId(), c.getName(), c.getTimezone(), c.getPlan(),
                        c.getStatus() == null ? null : c.getStatus().name()))
                .toList();
    }

    // ============================ get_client_report_data ============================

    @Transactional(readOnly = true)
    public ReportView getClientReportData(McpIdentity identity, Long clientId, String period) {
        access.requireClient(identity, clientId);
        Range range = McpPeriods.parse(period);
        ReportData data = summaryReport(clientId, range);
        List<PostView> topPosts = data.topPosts().stream()
                .map(p -> new PostView(p.id(), p.platform(), p.displayType(), p.publishedAtLocal(),
                        p.permalink(), p.caption(), p.metricName(), p.metricValue(), p.watchTimeSeconds()))
                .toList();
        return new ReportView(data.client(), data.period(), data.platforms(), data.rankBy(),
                data.kpis(), topPosts, data.narrativeMd());
    }

    // ============================ get_metric_series ============================

    @Transactional(readOnly = true)
    public AccountReportResponse getMetricSeries(McpIdentity identity, Long accountId, String metric,
                                                 LocalDate from, LocalDate to) {
        access.requireAccount(identity, accountId);
        if (metric == null || metric.isBlank()) {
            throw ApiException.badRequest("'metric' es obligatorio (una metric_key del catálogo)");
        }
        MetricsReportRequest request = new MetricsReportRequest(
                List.of(metric.trim()), new DateRange(from, to), "day");
        return metricReportService.accountReport(accountId, request);
    }

    // ============================ get_audience_demographics ============================

    @Transactional(readOnly = true)
    public DemographicsView getAudienceDemographics(McpIdentity identity, Long clientId, String period) {
        access.requireClient(identity, clientId);
        Range range = McpPeriods.parse(period);
        ReportData data = summaryReport(clientId, range);
        List<PlatformDemographics> byPlatform = data.kpis().stream()
                .filter(pk -> pk.demographics() != null)
                .map(pk -> new PlatformDemographics(pk.platform(), pk.demographics()))
                .toList();
        return new DemographicsView(data.client(), range.from(), range.to(), byPlatform);
    }

    // ============================ compare_periods ============================

    @Transactional(readOnly = true)
    public CompareView comparePeriods(McpIdentity identity, Long clientId, String periodA, String periodB) {
        access.requireClient(identity, clientId);
        Range a = McpPeriods.parse(periodA);
        Range b = McpPeriods.parse(periodB);
        ReportData da = summaryReport(clientId, a);
        ReportData db = summaryReport(clientId, b);

        Map<String, PlatformKpis> byPlatformA = indexByPlatform(da);
        Map<String, PlatformKpis> byPlatformB = indexByPlatform(db);
        Set<String> platforms = new LinkedHashSet<>();
        byPlatformA.keySet().forEach(platforms::add);
        byPlatformB.keySet().forEach(platforms::add);

        List<PlatformCompare> comparisons = new ArrayList<>();
        for (String platform : platforms) {
            PlatformKpis pa = byPlatformA.get(platform);
            PlatformKpis pb = byPlatformB.get(platform);
            comparisons.add(new PlatformCompare(platform,
                    compareMetrics(pa, pb),
                    pa == null ? null : pa.engagementRate(),
                    pb == null ? null : pb.engagementRate()));
        }
        return new CompareView(da.client(), new PeriodRef(a.from(), a.to()),
                new PeriodRef(b.from(), b.to()), comparisons);
    }

    private List<MetricCompare> compareMetrics(PlatformKpis a, PlatformKpis b) {
        Map<String, Kpi> metricsA = indexMetrics(a);
        Map<String, Kpi> metricsB = indexMetrics(b);
        Set<String> keys = new LinkedHashSet<>();
        metricsA.keySet().forEach(keys::add);
        metricsB.keySet().forEach(keys::add);

        List<MetricCompare> out = new ArrayList<>();
        for (String key : keys) {
            Kpi ka = metricsA.get(key);
            Kpi kb = metricsB.get(key);
            Kpi ref = ka != null ? ka : kb;
            BigDecimal valueA = ka == null ? null : ka.value();
            BigDecimal valueB = kb == null ? null : kb.value();
            BigDecimal delta = (valueA != null && valueB != null) ? valueB.subtract(valueA) : null;
            BigDecimal deltaPct = null;
            if (valueA != null && valueA.signum() != 0 && valueB != null) {
                deltaPct = valueB.subtract(valueA)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(valueA, 1, RoundingMode.HALF_UP);
            }
            out.add(new MetricCompare(key, ref.displayName(), ref.unit(), valueA, valueB, delta, deltaPct));
        }
        return out;
    }

    // ============================ get_posting_performance ============================

    @Transactional(readOnly = true)
    public PostingPerformanceView getPostingPerformance(McpIdentity identity, Long clientId, String by) {
        Client client = access.requireClient(identity, clientId);
        boolean byWeekday = resolveBucketing(by);
        ZoneId zone = clientZone(client);
        List<PostPerfRow> rows = reportQuery.findPostPerformance(clientId, REACH_KEYS, ENGAGEMENT_KEYS);

        Map<Integer, Accumulator> buckets = new LinkedHashMap<>();
        for (PostPerfRow row : rows) {
            if (row.publishedAt() == null) {
                continue;
            }
            ZonedDateTime local = row.publishedAt().atZone(zone);
            int order = byWeekday ? local.getDayOfWeek().getValue() : local.getHour();
            buckets.computeIfAbsent(order, k -> new Accumulator()).add(row.reach(), row.engagement());
        }
        List<Bucket> result = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().toBucket(bucketLabel(e.getKey(), byWeekday), e.getKey()))
                .toList();
        return new PostingPerformanceView(toClient(client), byWeekday ? "weekday" : "hour", result);
    }

    private static boolean resolveBucketing(String by) {
        String value = by == null ? "" : by.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "weekday", "dow", "day" -> true;
            case "hour" -> false;
            default -> throw ApiException.badRequest(
                    "'by' inválido: '%s'. Usá 'hour' o 'weekday'".formatted(by));
        };
    }

    private static String bucketLabel(int order, boolean byWeekday) {
        if (byWeekday) {
            return DayOfWeek.of(order).getDisplayName(TextStyle.FULL, ES);
        }
        return "%02d:00".formatted(order);
    }

    /** Acumula reach/engagement de los posts de un bucket para promediar sólo sobre los que tienen dato. */
    private static final class Accumulator {
        private int postCount;
        private BigDecimal reachSum = BigDecimal.ZERO;
        private int reachCount;
        private BigDecimal engagementSum = BigDecimal.ZERO;
        private int engagementCount;

        void add(BigDecimal reach, BigDecimal engagement) {
            postCount++;
            if (reach != null) {
                reachSum = reachSum.add(reach);
                reachCount++;
            }
            if (engagement != null) {
                engagementSum = engagementSum.add(engagement);
                engagementCount++;
            }
        }

        Bucket toBucket(String label, int order) {
            return new Bucket(label, order, postCount, average(reachSum, reachCount),
                    average(engagementSum, engagementCount));
        }

        private static BigDecimal average(BigDecimal sum, int count) {
            return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        }
    }

    // ============================ save_report_narrative (ÚNICA de escritura) ============================

    @Transactional
    public NarrativeSaved saveReportNarrative(McpIdentity identity, Long clientId, String period,
                                              String markdown, String model) {
        access.requireClient(identity, clientId);
        Range range = McpPeriods.parse(period);
        SaveResult result = narrativeService.save(
                clientId, range.from(), range.to(), markdown, "MCP", model, identity.userId());
        var report = result.report();
        return new NarrativeSaved(report.getId(), clientId, range.from(), range.to(),
                report.getNarrativeSource(), report.getNarrativeModel(), report.getNarrativeGeneratedAt(),
                result.createdNewReport());
    }

    // ============================ helpers ============================

    /** Mismo armado que {@code :preview}/export (SUMMARY: KPIs por red + bloques v1.1 + top posts). */
    private ReportData summaryReport(Long clientId, Range range) {
        return reportService.buildReportData(clientId, ReportType.SUMMARY, null,
                range.from(), range.to(), null, null, null);
    }

    private static Map<String, PlatformKpis> indexByPlatform(ReportData data) {
        Map<String, PlatformKpis> map = new LinkedHashMap<>();
        for (PlatformKpis pk : data.kpis()) {
            map.put(pk.platform(), pk);
        }
        return map;
    }

    private static Map<String, Kpi> indexMetrics(PlatformKpis platform) {
        Map<String, Kpi> map = new LinkedHashMap<>();
        if (platform != null) {
            for (Kpi kpi : platform.metrics()) {
                map.put(kpi.key(), kpi);
            }
        }
        return map;
    }

    private static ReportData.Client toClient(Client c) {
        return new ReportData.Client(c.getId(), c.getName(), c.getTimezone(), c.getPlan());
    }

    private static ZoneId clientZone(Client client) {
        try {
            return ZoneId.of(client.getTimezone());
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
