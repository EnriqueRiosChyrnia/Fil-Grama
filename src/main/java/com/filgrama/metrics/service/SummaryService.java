package com.filgrama.metrics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private PlatformSummary buildPlatformSummary(Long clientId, String platform,
                                                 LocalDate from, LocalDate to) {
        // Una sola consulta por métrica ACCOUNT del catálogo de esa red; cache local para los derivados.
        Map<String, MetricAgg> aggByKey = new LinkedHashMap<>();
        List<SummaryMetric> metrics = new ArrayList<>();
        for (MetricCatalogItem item : catalog.list(platform, "ACCOUNT")) {
            MetricAgg agg = queryRepository.accountMetricAgg(clientId, platform, item.key(), from, to);
            aggByKey.put(item.key(), agg);
            metrics.add(new SummaryMetric(
                    item.key(), item.displayName(), item.unit(),
                    MetricFormat.clean(agg.latest()), MetricFormat.clean(agg.total())));
        }

        DerivedRoles roles = ROLES.get(platform);
        BigDecimal engagementRate = roles == null ? null : engagementRate(roles, aggByKey);
        BigDecimal followerGrowth = roles == null ? null : followerGrowth(roles, aggByKey);
        return new PlatformSummary(platform, metrics, engagementRate, followerGrowth);
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
