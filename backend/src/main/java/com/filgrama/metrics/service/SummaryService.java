package com.filgrama.metrics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.SocialAccount;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.MetricFormat;
import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.metrics.dto.PlatformSummary;
import com.filgrama.metrics.dto.SummaryMetric;
import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.repository.MetricsQueryRepository;
import com.filgrama.metrics.repository.MetricsQueryRepository.MetricAgg;
import com.filgrama.metrics.repository.MetricsQueryRepository.SeriesRow;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * KPIs agregados por red para un cliente. Dirigido por el catálogo: para cada red que el cliente
 * tiene conectada, agrega sus métricas CORE de nivel ACCOUNT y deriva {@code engagementRate}
 * (interacciones / alcance, con fallback a seguidores) y {@code followerGrowth}
 * (followers(último) − followers(primero)). Filtra todo por {@code client_id}.
 */
@Service
public class SummaryService {

    /** Rol de cada red para los derivados (qué key es interacciones/alcance/seguidores). */
    private record DerivedRoles(String interactionsKey, String reachKey, String followersKey) {
    }

    /** Sin asumir paridad entre redes: cada una declara sólo los insumos que tiene en CORE. */
    private static final Map<String, DerivedRoles> ROLES = Map.of(
            "INSTAGRAM", new DerivedRoles("ig_total_interactions", "ig_reach", "ig_followers_count"),
            "FACEBOOK", new DerivedRoles("fb_page_post_engagements", "fb_page_views", null),
            "TIKTOK", new DerivedRoles(null, null, "tt_follower_count"));

    private final ClientRepository clientRepository;
    private final SocialAccountRepository accountRepository;
    private final MetricCatalogService catalog;
    private final MetricsQueryRepository queryRepository;

    public SummaryService(ClientRepository clientRepository,
                          SocialAccountRepository accountRepository,
                          MetricCatalogService catalog,
                          MetricsQueryRepository queryRepository) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.catalog = catalog;
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long clientId, LocalDate from, LocalDate to, String platformFilter) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
        }
        if (!clientRepository.existsById(clientId)) {
            throw ApiException.notFound("Client %d not found".formatted(clientId));
        }
        String wanted = platformFilter == null || platformFilter.isBlank()
                ? null : platformFilter.trim().toUpperCase(Locale.ROOT);

        // Redes que el cliente tiene conectadas, en orden estable.
        List<String> platforms = accountRepository.findByClientId(clientId).stream()
                .map(SocialAccount::getPlatform)
                .map(Enum::name)
                .distinct()
                .filter(p -> wanted == null || wanted.equals(p))
                .sorted()
                .toList();

        List<PlatformSummary> result = new ArrayList<>();
        for (String platform : platforms) {
            result.add(buildPlatformSummary(clientId, platform, from, to));
        }
        return new SummaryResponse(clientId, from, to, result);
    }

    /**
     * Variante account-scoped para el <b>reporte por cuenta</b> (CU5, track Reportes): agrega SÓLO las
     * cuentas dadas (las del cliente que estén en {@code accountIds}), agrupándolas por su red, y deriva
     * los mismos KPIs que {@link #summary}. Reutiliza {@link MetricsQueryRepository#accountMetricSeries}
     * (la única consulta account-scoped existente) y combina por red sumando total/último/primero por
     * cuenta — exactamente las reglas del agregado por red de {@code accountMetricAgg}. {@code accountIds}
     * vacío ⇒ sin redes. Multi-tenant: filtra por {@code client_id}, así una cuenta de otro cliente no
     * aporta nada (la validación dura de pertenencia ocurre en el track Reportes, antes de llamar).
     */
    @Transactional(readOnly = true)
    public SummaryResponse summaryForAccounts(Long clientId, LocalDate from, LocalDate to,
                                              Collection<Long> accountIds) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
        }
        if (!clientRepository.existsById(clientId)) {
            throw ApiException.notFound("Client %d not found".formatted(clientId));
        }
        if (accountIds == null || accountIds.isEmpty()) {
            return new SummaryResponse(clientId, from, to, List.of());
        }

        // Cuentas del cliente que están en el subconjunto pedido, agrupadas por red (orden estable).
        Map<String, List<Long>> accountsByPlatform = new TreeMap<>();
        for (SocialAccount account : accountRepository.findByClientId(clientId)) {
            if (accountIds.contains(account.getId())) {
                accountsByPlatform.computeIfAbsent(account.getPlatform().name(), k -> new ArrayList<>())
                        .add(account.getId());
            }
        }

        List<PlatformSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : accountsByPlatform.entrySet()) {
            result.add(buildPlatformSummary(clientId, entry.getKey(), entry.getValue(), from, to));
        }
        return new SummaryResponse(clientId, from, to, result);
    }

    /** KPIs de una red sumando TODAS las cuentas del cliente (agregado por red de {@code accountMetricAgg}). */
    private PlatformSummary buildPlatformSummary(Long clientId, String platform,
                                                 LocalDate from, LocalDate to) {
        // Una sola consulta por métrica ACCOUNT del catálogo de esa red; cache local para los derivados.
        List<MetricCatalogItem> items = catalog.list(platform, "ACCOUNT");
        Map<String, MetricAgg> aggByKey = new LinkedHashMap<>();
        for (MetricCatalogItem item : items) {
            aggByKey.put(item.key(), queryRepository.accountMetricAgg(clientId, platform, item.key(), from, to));
        }
        return toPlatformSummary(platform, items, aggByKey);
    }

    /** KPIs de una red restringidos a un subconjunto de cuentas (reporte por cuenta). */
    private PlatformSummary buildPlatformSummary(Long clientId, String platform, List<Long> accountIds,
                                                 LocalDate from, LocalDate to) {
        List<MetricCatalogItem> items = catalog.list(platform, "ACCOUNT");
        List<String> keys = items.stream().map(MetricCatalogItem::key).toList();
        Map<String, MetricAgg> aggByKey = aggregateAccounts(clientId, accountIds, keys, from, to);
        return toPlatformSummary(platform, items, aggByKey);
    }

    /** Arma el {@link PlatformSummary} (métricas CORE + derivados) a partir del agregado por métrica. */
    private PlatformSummary toPlatformSummary(String platform, List<MetricCatalogItem> items,
                                              Map<String, MetricAgg> aggByKey) {
        List<SummaryMetric> metrics = new ArrayList<>();
        for (MetricCatalogItem item : items) {
            MetricAgg agg = aggByKey.get(item.key());
            metrics.add(new SummaryMetric(
                    item.key(), item.displayName(), item.unit(),
                    MetricFormat.clean(agg.latest()), MetricFormat.clean(agg.total())));
        }
        DerivedRoles roles = ROLES.get(platform);
        BigDecimal engagementRate = roles == null ? null : engagementRate(roles, aggByKey);
        BigDecimal followerGrowth = roles == null ? null : followerGrowth(roles, aggByKey);
        return new PlatformSummary(platform, metrics, engagementRate, followerGrowth);
    }

    /**
     * Agrega cada métrica del conjunto de cuentas: por cuenta calcula total (suma de snapshots), último
     * y primero (por {@code captured_at}), y luego suma esos por cuenta — equivalente account-scoped a
     * {@link MetricsQueryRepository#accountMetricAgg}. Una sola query por cuenta (todas sus métricas);
     * la serie viene ordenada por {@code (metric_key, captured_at)}, así el último elemento de cada
     * métrica es el más reciente y el primero el más antiguo. Sin datos ⇒ ceros (mismo COALESCE que el
     * agregado por red).
     */
    private Map<String, MetricAgg> aggregateAccounts(Long clientId, List<Long> accountIds,
                                                     List<String> keys, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, BigDecimal> latests = new LinkedHashMap<>();
        Map<String, BigDecimal> earliests = new LinkedHashMap<>();
        for (String key : keys) {
            totals.put(key, BigDecimal.ZERO);
            latests.put(key, BigDecimal.ZERO);
            earliests.put(key, BigDecimal.ZERO);
        }
        if (!keys.isEmpty()) {
            for (Long accountId : accountIds) {
                Map<String, List<BigDecimal>> byMetric = new LinkedHashMap<>();
                for (SeriesRow row : queryRepository.accountMetricSeries(clientId, accountId, keys, from, to)) {
                    BigDecimal value = row.value() == null ? BigDecimal.ZERO : row.value();
                    byMetric.computeIfAbsent(row.metricKey(), k -> new ArrayList<>()).add(value);
                }
                for (Map.Entry<String, List<BigDecimal>> e : byMetric.entrySet()) {
                    List<BigDecimal> values = e.getValue();
                    if (values.isEmpty() || !totals.containsKey(e.getKey())) {
                        continue;
                    }
                    BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    totals.merge(e.getKey(), total, BigDecimal::add);
                    latests.merge(e.getKey(), values.get(values.size() - 1), BigDecimal::add);
                    earliests.merge(e.getKey(), values.get(0), BigDecimal::add);
                }
            }
        }
        Map<String, MetricAgg> out = new LinkedHashMap<>();
        for (String key : keys) {
            out.put(key, new MetricAgg(totals.get(key), latests.get(key), earliests.get(key)));
        }
        return out;
    }

    /** interacciones(total) / alcance(total); si la red no tiene alcance, cae a seguidores(último). */
    private static BigDecimal engagementRate(DerivedRoles roles, Map<String, MetricAgg> aggByKey) {
        if (roles.interactionsKey() == null) {
            return null;
        }
        MetricAgg interactions = aggByKey.get(roles.interactionsKey());
        if (interactions == null) {
            return null;
        }
        BigDecimal denominator = null;
        if (roles.reachKey() != null && aggByKey.containsKey(roles.reachKey())) {
            denominator = aggByKey.get(roles.reachKey()).total();
        } else if (roles.followersKey() != null && aggByKey.containsKey(roles.followersKey())) {
            denominator = aggByKey.get(roles.followersKey()).latest();
        }
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return interactions.total().divide(denominator, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** followers(último) − followers(primero) en el período. */
    private static BigDecimal followerGrowth(DerivedRoles roles, Map<String, MetricAgg> aggByKey) {
        if (roles.followersKey() == null || !aggByKey.containsKey(roles.followersKey())) {
            return null;
        }
        MetricAgg followers = aggByKey.get(roles.followersKey());
        return MetricFormat.clean(followers.latest().subtract(followers.earliest()));
    }
}
