package com.filgrama.reports.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.PlatformSummary;
import com.filgrama.metrics.dto.SummaryMetric;
import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.service.SummaryService;
import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportQueryRepository;
import com.filgrama.reports.ReportQueryRepository.PostMetricValue;
import com.filgrama.reports.ReportQueryRepository.PostRow;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.Period;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.PostGroup;
import com.filgrama.reports.data.ReportData.ReachEvolution;
import com.filgrama.reports.data.ReportData.ReportPost;
import com.filgrama.reports.render.ThumbnailLoader;
import com.filgrama.reports.render.ThumbnailLoader.Thumbnail;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Arma el {@link ReportData} con números que provee la app: <b>reusa los servicios públicos del
 * track D</b> (vía {@link SummaryService} para KPIs por red, derivados y deltas vs el período
 * anterior) y consulta los posts del cliente con {@link ReportQueryRepository} (query propia de
 * reporte, nivel cliente, agrupable por red/tipo — los servicios de D son per-cuenta). Las
 * miniaturas las resuelve {@link ThumbnailLoader} (storage del track E). Multi-tenant: todo cuelga
 * de {@code client_id}. <b>No inventa cifras</b>: si un dato falta, queda null.
 */
@Component
public class ReportDataAssembler {

    private static final int TOP_N = 3;
    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy",
            Locale.forLanguageTag("es"));

    /** Métrica de alcance a nivel cuenta por red (para "evolución del alcance"). null = la red no tiene. */
    private static final Map<String, String> ACCOUNT_REACH_KEY = Map.of(
            "INSTAGRAM", "ig_reach",
            "FACEBOOK", "fb_page_views_total");

    /** Orden de las secciones por tipo dentro de una red. */
    private static final List<String> TYPE_ORDER = List.of("Reels", "Feed", "Videos", "Stories", "Otros");

    private final ClientRepository clientRepository;
    private final SocialAccountRepository accountRepository;
    private final SummaryService summaryService;
    private final ReportQueryRepository reportQuery;
    private final RankMetricResolver rankResolver;
    private final ThumbnailLoader thumbnailLoader;

    public ReportDataAssembler(ClientRepository clientRepository,
                               SocialAccountRepository accountRepository,
                               SummaryService summaryService,
                               ReportQueryRepository reportQuery,
                               RankMetricResolver rankResolver,
                               ThumbnailLoader thumbnailLoader) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.summaryService = summaryService;
        this.reportQuery = reportQuery;
        this.rankResolver = rankResolver;
        this.thumbnailLoader = thumbnailLoader;
    }

    @Transactional(readOnly = true)
    public ReportData assemble(Long clientId, ReportType type, ReportFormat format,
                               LocalDate from, LocalDate to, List<String> requestedPlatforms, String rankBy) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("Client %d not found".formatted(clientId)));
        validateRange(from, to);
        rankResolver.validate(rankBy);
        String normalizedRankBy = rankResolver.normalize(rankBy);

        List<String> platforms = resolvePlatforms(clientId, requestedPlatforms);
        ZoneId zone = clientZone(client);

        // ---- KPIs por red + deltas vs período anterior (servicios del track D) ----
        Period period = previousPeriod(from, to);
        SummaryResponse current = summaryService.summary(clientId, from, to, null);
        SummaryResponse previous = summaryService.summary(clientId, period.previousFrom(), period.previousTo(), null);
        List<PlatformKpis> kpis = buildKpis(platforms, current, previous);

        // ---- Posts del cliente en el período (query propia de reporte) ----
        List<PostRow> rows = reportQuery.findPosts(clientId, from, to, platforms);
        Map<Long, Map<String, BigDecimal>> metricsByPost = loadPostMetrics(clientId, rows, from, to);
        List<ReportPost> posts = rows.stream()
                .map(r -> toBarePost(r, zone, normalizedRankBy, metricsByPost))
                .toList();

        if (type == ReportType.SUMMARY) {
            List<ReportPost> top = enrich(topBy(posts.stream().filter(p -> !p.story()).toList(), true, TOP_N));
            return new ReportData(type, format, toClient(client), period, platforms, normalizedRankBy,
                    kpis, top, List.of(), List.of(), emptyHighlights(), emptyHighlights(), null);
        }

        // EXTENDED: cada post con miniatura, agrupado por red/tipo + destacadas/margen.
        List<ReportPost> enriched = enrich(posts);
        List<ReportPost> postItems = enriched.stream().filter(p -> !p.story()).toList();
        List<ReportPost> storyItems = enriched.stream().filter(ReportPost::story).toList();

        List<PostGroup> postGroups = groupByPlatformAndType(postItems);
        List<PostGroup> storyGroups = groupByPlatformAndType(storyItems);
        Highlights postHighlights = highlights(postItems);
        Highlights storyHighlights = highlights(storyItems);

        return new ReportData(type, format, toClient(client), period, platforms, normalizedRankBy,
                kpis, postHighlights.top(), postGroups, storyGroups, postHighlights, storyHighlights, null);
    }

    // ============================ KPIs ============================

    private List<PlatformKpis> buildKpis(List<String> platforms, SummaryResponse current, SummaryResponse previous) {
        Map<String, PlatformSummary> currByPlatform = byPlatform(current);
        Map<String, PlatformSummary> prevByPlatform = byPlatform(previous);

        List<PlatformKpis> result = new ArrayList<>();
        for (String platform : platforms) {
            PlatformSummary cur = currByPlatform.get(platform);
            if (cur == null) {
                // Red sin datos en el período: igual se lista (desglose por red), con KPIs vacíos.
                result.add(new PlatformKpis(platform, List.of(), null, null, null));
                continue;
            }
            PlatformSummary prev = prevByPlatform.get(platform);
            Map<String, SummaryMetric> prevMetrics = prev == null ? Map.of() : index(prev.metrics());

            List<Kpi> kpiList = new ArrayList<>();
            for (SummaryMetric m : cur.metrics()) {
                BigDecimal value = kpiValue(m);
                SummaryMetric pm = prevMetrics.get(m.metric());
                BigDecimal delta = pm == null ? null : clean(value.subtract(kpiValue(pm)));
                kpiList.add(new Kpi(m.metric(), m.displayName(), m.unit(), value, delta));
            }
            ReachEvolution reach = reachEvolution(platform, cur, prev);
            result.add(new PlatformKpis(platform, kpiList, cur.engagementRate(), cur.followerGrowth(), reach));
        }
        return result;
    }

    /** Valor del KPI: los stocks (seguidores) usan el último; los flujos, el total del período. */
    private static BigDecimal kpiValue(SummaryMetric m) {
        boolean stock = m.metric().endsWith("follower_count") || m.metric().endsWith("followers_count");
        BigDecimal v = stock ? m.latest() : m.total();
        return v == null ? BigDecimal.ZERO : v;
    }

    private ReachEvolution reachEvolution(String platform, PlatformSummary cur, PlatformSummary prev) {
        String reachKey = ACCOUNT_REACH_KEY.get(platform);
        if (reachKey == null) {
            return null;
        }
        BigDecimal curReach = metricTotal(cur, reachKey);
        if (curReach == null) {
            return null;
        }
        BigDecimal prevReach = prev == null ? null : metricTotal(prev, reachKey);
        BigDecimal deltaPct = null;
        if (prevReach != null && prevReach.signum() != 0) {
            deltaPct = curReach.subtract(prevReach)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevReach, 1, RoundingMode.HALF_UP);
        }
        return new ReachEvolution(clean(curReach), clean(prevReach), deltaPct);
    }

    private static BigDecimal metricTotal(PlatformSummary ps, String key) {
        return ps.metrics().stream()
                .filter(m -> m.metric().equals(key))
                .map(SummaryMetric::total)
                .findFirst()
                .orElse(null);
    }

    private static Map<String, PlatformSummary> byPlatform(SummaryResponse summary) {
        Map<String, PlatformSummary> map = new HashMap<>();
        if (summary != null && summary.platforms() != null) {
            for (PlatformSummary ps : summary.platforms()) {
                map.put(ps.platform(), ps);
            }
        }
        return map;
    }

    private static Map<String, SummaryMetric> index(List<SummaryMetric> metrics) {
        Map<String, SummaryMetric> map = new HashMap<>();
        for (SummaryMetric m : metrics) {
            map.put(m.metric(), m);
        }
        return map;
    }

    // ============================ Posts ============================

    private Map<Long, Map<String, BigDecimal>> loadPostMetrics(Long clientId, List<PostRow> rows,
                                                               LocalDate from, LocalDate to) {
        List<Long> ids = rows.stream().map(PostRow::id).toList();
        Map<Long, Map<String, BigDecimal>> byPost = new HashMap<>();
        for (PostMetricValue v : reportQuery.latestPostMetrics(clientId, ids, from, to)) {
            byPost.computeIfAbsent(v.postId(), k -> new HashMap<>()).put(v.metricKey(), v.value());
        }
        return byPost;
    }

    private ReportPost toBarePost(PostRow r, ZoneId zone, String rankBy,
                                  Map<Long, Map<String, BigDecimal>> metricsByPost) {
        boolean story = r.ephemeral() || "STORY".equals(r.postType());
        var resolved = rankResolver.resolve(r.platform(), story, rankBy);
        BigDecimal value = null;
        String metricKey = null;
        String metricName = null;
        if (resolved != null) {
            metricKey = resolved.key();
            metricName = resolved.displayName();
            Map<String, BigDecimal> vals = metricsByPost.get(r.id());
            value = vals == null ? null : vals.get(metricKey);
        }
        // En el bare guardamos el remoto del post como candidato; enrich() decide cache vs remoto.
        return new ReportPost(
                r.id(), r.platform(), r.postType(), displayType(r.postType(), story),
                r.publishedAt(), localDate(r.publishedAt(), zone), r.permalink(), r.caption(),
                null, r.remoteThumbnailUrl(), story, metricKey, metricName, clean(value));
    }

    /** Resuelve la miniatura (data-URI base64 cacheada o remoto diferido) de cada post a renderizar. */
    private List<ReportPost> enrich(List<ReportPost> posts) {
        List<ReportPost> out = new ArrayList<>(posts.size());
        for (ReportPost p : posts) {
            Thumbnail thumb = thumbnailLoader.load(p.id(), p.thumbnailUrl());
            out.add(p.withThumbnail(thumb.dataUri(), thumb.remoteUrl()));
        }
        return out;
    }

    private static List<PostGroup> groupByPlatformAndType(List<ReportPost> posts) {
        Map<String, List<ReportPost>> grouped = new LinkedHashMap<>();
        for (ReportPost p : posts) {
            grouped.computeIfAbsent(p.platform() + " " + p.displayType(), k -> new ArrayList<>()).add(p);
        }
        return grouped.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split(" ", 2);
                    return new PostGroup(parts[0], parts[1], e.getValue());
                })
                .sorted(Comparator
                        .comparing(PostGroup::platform)
                        .thenComparingInt(g -> typeRank(g.displayType())))
                .toList();
    }

    private Highlights highlights(List<ReportPost> posts) {
        List<ReportPost> ranked = posts.stream().filter(p -> p.metricValue() != null).toList();
        List<ReportPost> top = topBy(ranked, true, TOP_N);
        List<ReportPost> bottom = topBy(ranked, false, TOP_N);
        return new Highlights(top, bottom);
    }

    /** Top/bottom por valor de la métrica de ranking; null queda al final cuando se pide top. */
    private static List<ReportPost> topBy(List<ReportPost> posts, boolean desc, int n) {
        Comparator<ReportPost> byValue = Comparator.comparing(
                ReportPost::metricValue, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<ReportPost> cmp = desc ? byValue.reversed() : byValue;
        return posts.stream().sorted(cmp).limit(n).toList();
    }

    // ============================ Helpers ============================

    private List<String> resolvePlatforms(Long clientId, List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested.stream()
                    .map(p -> p == null ? "" : p.trim().toUpperCase(Locale.ROOT))
                    .map(ReportDataAssembler::requireValidPlatform)
                    .distinct()
                    .sorted()
                    .toList();
        }
        // Sin filtro explícito: todas las redes conectadas del cliente.
        return accountRepository.findByClientId(clientId).stream()
                .map(SocialAccount::getPlatform)
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
    }

    private static String requireValidPlatform(String name) {
        try {
            return Platform.valueOf(name).name();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    "plataforma inválida: '%s' (use INSTAGRAM|FACEBOOK|TIKTOK)".formatted(name));
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("'from' y 'to' son obligatorios");
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
        }
    }

    private static Period previousPeriod(LocalDate from, LocalDate to) {
        long len = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(len - 1);
        return new Period(from, to, prevFrom, prevTo);
    }

    private static ZoneId clientZone(Client client) {
        try {
            return ZoneId.of(client.getTimezone());
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }

    private static String localDate(Instant instant, ZoneId zone) {
        return instant == null ? "" : LOCAL_DATE.format(instant.atZone(zone));
    }

    private static String displayType(String postType, boolean story) {
        if (story) {
            return "Stories";
        }
        if (postType == null) {
            return "Otros";
        }
        return switch (postType) {
            case "REEL" -> "Reels";
            case "IMAGE", "CAROUSEL" -> "Feed";
            case "VIDEO" -> "Videos";
            case "STORY" -> "Stories";
            default -> "Otros";
        };
    }

    private static int typeRank(String displayType) {
        int idx = TYPE_ORDER.indexOf(displayType);
        return idx < 0 ? TYPE_ORDER.size() : idx;
    }

    private static Highlights emptyHighlights() {
        return new Highlights(List.of(), List.of());
    }

    private static ReportData.Client toClient(Client c) {
        return new ReportData.Client(c.getId(), c.getName(), c.getTimezone(), c.getPlan());
    }

    private static BigDecimal clean(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
