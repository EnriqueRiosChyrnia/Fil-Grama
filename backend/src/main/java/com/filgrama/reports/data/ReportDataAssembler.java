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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Client;
import com.filgrama.domain.Metric;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.PlatformSummary;
import com.filgrama.metrics.dto.SummaryMetric;
import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.service.MetricCatalogService;
import com.filgrama.metrics.service.SummaryService;
import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportQueryRepository;
import com.filgrama.reports.ReportQueryRepository.DemographicRow;
import com.filgrama.reports.ReportQueryRepository.PlatformMetricKey;
import com.filgrama.reports.ReportQueryRepository.PostMetricValue;
import com.filgrama.reports.ReportQueryRepository.PostRow;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData.ContentTypeShare;
import com.filgrama.reports.data.ReportData.Demographics;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.Period;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.PostGroup;
import com.filgrama.reports.data.ReportData.ProfileActivity;
import com.filgrama.reports.data.ReportData.ReachEvolution;
import com.filgrama.reports.data.ReportData.ReportPost;
import com.filgrama.reports.data.ReportData.Segment;
import com.filgrama.reports.data.ReportData.ViewsFollowerSplit;
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

    // ---- v1.1: reporte mensual completo de Instagram (research/06, spec/05 §v1.1) ----

    /** Keys del split seguidor/no-seguidor de VISUALIZACIONES por red (el de interacciones no existe). */
    private static final Map<String, String[]> VIEWS_SPLIT_KEYS = Map.of(
            "INSTAGRAM", new String[] {"ig_views_followers", "ig_views_non_followers"});

    /** Keys de actividad de perfil (visitas, taps) por red: {profileViews, whatsappTaps, directionTaps}. */
    private static final Map<String, String[]> PROFILE_ACTIVITY_KEYS = Map.of(
            "INSTAGRAM", new String[] {"ig_profile_views", "ig_taps_whatsapp", "ig_taps_direction"});

    /** Keys POST de interacciones "por acción" (likes/comentarios/compartidos/guardados/reposts) por red. */
    private static final Map<String, List<String>> INTERACTION_ACTION_KEYS = Map.of(
            "INSTAGRAM", List.of("ig_post_likes", "ig_post_comments", "ig_post_shares",
                    "ig_post_saved", "ig_post_reposts"));

    /** Key POST de "visualizaciones" usada para el desglose por tipo de contenido, por red. */
    private static final Map<String, String> POST_VIEWS_KEY = Map.of(
            "INSTAGRAM", "ig_post_views",
            "FACEBOOK", "fb_post_views",
            "TIKTOK", "tt_view_count");

    /** Key POST de tiempo medio de visionado (reels), por red. */
    private static final Map<String, String> WATCH_TIME_KEY = Map.of(
            "INSTAGRAM", "ig_reels_avg_watch_time");

    private final ClientRepository clientRepository;
    private final SocialAccountRepository accountRepository;
    private final SummaryService summaryService;
    private final ReportQueryRepository reportQuery;
    private final RankMetricResolver rankResolver;
    private final ThumbnailLoader thumbnailLoader;
    private final MetricCatalogService catalog;

    public ReportDataAssembler(ClientRepository clientRepository,
                               SocialAccountRepository accountRepository,
                               SummaryService summaryService,
                               ReportQueryRepository reportQuery,
                               RankMetricResolver rankResolver,
                               ThumbnailLoader thumbnailLoader,
                               MetricCatalogService catalog) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.summaryService = summaryService;
        this.reportQuery = reportQuery;
        this.rankResolver = rankResolver;
        this.thumbnailLoader = thumbnailLoader;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public ReportData assemble(Long clientId, ReportType type, ReportFormat format,
                               LocalDate from, LocalDate to, List<String> requestedPlatforms,
                               List<Long> requestedAccountIds, String rankBy) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("Client %d not found".formatted(clientId)));
        validateRange(from, to);
        rankResolver.validate(rankBy);
        String normalizedRankBy = rankResolver.normalize(rankBy);

        // Alcance del reporte: por CUENTA (accountIds, derivando la red) o por RED (compat). En el
        // modo por cuenta, accountIds != null y todas las queries se restringen a esas cuentas.
        Scope scope = resolveScope(clientId, requestedPlatforms, requestedAccountIds);
        List<String> platforms = scope.platforms();
        List<Long> accountIds = scope.accountIds();
        ZoneId zone = clientZone(client);

        // ---- KPIs por red + deltas vs período anterior (servicios del track D) ----
        Period period = previousPeriod(from, to);
        SummaryResponse current = summary(clientId, from, to, accountIds);
        SummaryResponse previous = summary(clientId, period.previousFrom(), period.previousTo(), accountIds);
        // Qué métricas tienen baseline real en el período anterior (distingue "previo ausente" de "previo = 0").
        Set<PlatformMetricKey> prevBaseline = reportQuery.accountMetricKeysPresent(
                clientId, platforms, accountIds, period.previousFrom(), period.previousTo());
        List<PlatformKpis> baseKpis = buildKpis(platforms, current, previous, prevBaseline);

        // ---- Posts del cliente en el período (query propia de reporte) — se cargan ANTES de cerrar los
        // KPIs porque alimentan los bloques v1.1 derivados (interacciones por acción, visualizaciones por
        // tipo de contenido; research/06 §3). ----
        List<PostRow> rows = reportQuery.findPosts(clientId, from, to, platforms, accountIds);
        Map<Long, Map<String, BigDecimal>> metricsByPost = loadPostMetrics(clientId, rows, from, to);
        List<ReportPost> posts = rows.stream()
                .map(r -> toBarePost(r, zone, normalizedRankBy, metricsByPost))
                .toList();

        // ---- Demografía de seguidores (v1.1, tabla audience_demographics; research/06 §3) ----
        List<DemographicRow> demographicRows = reportQuery.findDemographics(clientId, platforms, accountIds, from, to);

        List<PlatformKpis> kpis = enrichKpis(baseKpis, rows, metricsByPost, demographicRows);

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

    private List<PlatformKpis> buildKpis(List<String> platforms, SummaryResponse current,
                                         SummaryResponse previous, Set<PlatformMetricKey> prevBaseline) {
        Map<String, PlatformSummary> currByPlatform = byPlatform(current);
        Map<String, PlatformSummary> prevByPlatform = byPlatform(previous);

        List<PlatformKpis> result = new ArrayList<>();
        for (String platform : platforms) {
            PlatformSummary cur = currByPlatform.get(platform);
            if (cur == null) {
                // Red sin datos en el período: igual se lista (desglose por red), con KPIs vacíos.
                result.add(new PlatformKpis(platform, List.of(), null, null, null,
                        null, null, List.of(), List.of(), null));
                continue;
            }
            PlatformSummary prev = prevByPlatform.get(platform);
            Map<String, SummaryMetric> prevMetrics = prev == null ? Map.of() : index(prev.metrics());

            List<Kpi> kpiList = new ArrayList<>();
            for (SummaryMetric m : cur.metrics()) {
                BigDecimal value = kpiValue(m);
                // Sin baseline real en el previo (no hay snapshot) → delta null, NO el valor completo.
                boolean hasBaseline = prevBaseline.contains(new PlatformMetricKey(platform, m.metric()));
                SummaryMetric pm = prevMetrics.get(m.metric());
                BigDecimal delta = (hasBaseline && pm != null) ? clean(value.subtract(kpiValue(pm))) : null;
                kpiList.add(new Kpi(m.metric(), m.displayName(), m.unit(), value, delta));
            }
            ReachEvolution reach = reachEvolution(platform, cur, prev, prevBaseline);
            // Los bloques v1.1 (demografía, split, interacciones por acción, tipo de contenido, actividad
            // de perfil) se completan después en enrichKpis: acá dejamos placeholders vacíos/nulos.
            result.add(new PlatformKpis(platform, kpiList, cur.engagementRate(), cur.followerGrowth(), reach,
                    null, null, List.of(), List.of(), null));
        }
        return result;
    }

    // ============================ v1.1: bloques del reporte mensual completo ============================

    /**
     * Completa cada {@link PlatformKpis} con los bloques v1.1 del reporte (research/06 §3, spec/05
     * §v1.1): demografía, split seguidor/no-seguidor de visualizaciones y actividad de perfil (extraídos
     * de los KPIs ACCOUNT ya armados), más interacciones por acción y visualizaciones por tipo de
     * contenido (derivados de los posts del período). Todo nullable/vacío si la red no lo trae.
     */
    private List<PlatformKpis> enrichKpis(List<PlatformKpis> base, List<PostRow> rows,
                                          Map<Long, Map<String, BigDecimal>> metricsByPost,
                                          List<DemographicRow> demographicRows) {
        Map<String, List<DemographicRow>> demographicsByPlatform = demographicRows.stream()
                .collect(Collectors.groupingBy(DemographicRow::platform));
        List<PlatformKpis> result = new ArrayList<>();
        for (PlatformKpis pk : base) {
            String platform = pk.platform();
            Demographics demographics = demographics(demographicsByPlatform.get(platform));
            ViewsFollowerSplit split = viewsFollowerSplit(platform, pk.metrics());
            ProfileActivity activity = profileActivity(platform, pk.metrics());
            List<Kpi> interactions = interactionsByAction(platform, rows, metricsByPost);
            List<ContentTypeShare> contentTypeViews = viewsByContentType(platform, rows, metricsByPost);
            result.add(new PlatformKpis(platform, pk.metrics(), pk.engagementRate(), pk.followerGrowth(),
                    pk.reach(), demographics, split, interactions, contentTypeViews, activity));
        }
        return result;
    }

    /** Extrae el par (followers/nonFollowers) de {@code metrics} ya armado; null si la red no lo trae. */
    private static ViewsFollowerSplit viewsFollowerSplit(String platform, List<Kpi> metrics) {
        String[] keys = VIEWS_SPLIT_KEYS.get(platform);
        if (keys == null) {
            return null;
        }
        BigDecimal followers = kpiValueByKey(metrics, keys[0]);
        BigDecimal nonFollowers = kpiValueByKey(metrics, keys[1]);
        if (followers == null || nonFollowers == null) {
            return null;
        }
        BigDecimal total = followers.add(nonFollowers);
        BigDecimal followerPct = pct(followers, total);
        BigDecimal nonFollowerPct = pct(nonFollowers, total);
        return new ViewsFollowerSplit(clean(followers), clean(nonFollowers), followerPct, nonFollowerPct);
    }

    /** Extrae visitas al perfil + taps de {@code metrics} ya armado; null si la red no lo trae. */
    private static ProfileActivity profileActivity(String platform, List<Kpi> metrics) {
        String[] keys = PROFILE_ACTIVITY_KEYS.get(platform);
        if (keys == null) {
            return null;
        }
        BigDecimal views = kpiValueByKey(metrics, keys[0]);
        BigDecimal whatsapp = kpiValueByKey(metrics, keys[1]);
        BigDecimal direction = kpiValueByKey(metrics, keys[2]);
        if (views == null && whatsapp == null && direction == null) {
            return null;
        }
        return new ProfileActivity(views, whatsapp, direction);
    }

    private static BigDecimal kpiValueByKey(List<Kpi> metrics, String key) {
        return metrics.stream().filter(k -> k.key().equals(key)).map(Kpi::value).findFirst().orElse(null);
    }

    /**
     * Suma, por acción (likes/comentarios/compartidos/guardados/reposts), el valor de todos los posts
     * de la red en el período (keys {@code ig_post_*}; research/06 §1/§3). Vacía si la red no tiene
     * acciones mapeadas ({@link #INTERACTION_ACTION_KEYS}).
     */
    private List<Kpi> interactionsByAction(String platform, List<PostRow> rows,
                                           Map<Long, Map<String, BigDecimal>> metricsByPost) {
        List<String> keys = INTERACTION_ACTION_KEYS.get(platform);
        if (keys == null) {
            return List.of();
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (String key : keys) {
            totals.put(key, BigDecimal.ZERO);
        }
        for (PostRow r : rows) {
            if (!platform.equals(r.platform())) {
                continue;
            }
            Map<String, BigDecimal> vals = metricsByPost.get(r.id());
            if (vals == null) {
                continue;
            }
            for (String key : keys) {
                BigDecimal v = vals.get(key);
                if (v != null) {
                    totals.merge(key, v, BigDecimal::add);
                }
            }
        }
        List<Kpi> out = new ArrayList<>();
        for (String key : keys) {
            Metric m = catalog.find(key).orElse(null);
            String displayName = m != null ? m.getDisplayName() : key;
            String unit = m != null ? m.getUnit() : "count";
            out.add(new Kpi(key, displayName, unit, clean(totals.get(key)), null));
        }
        return out;
    }

    /**
     * Agrega las visualizaciones de los posts del período por {@code displayType} (Reels/Feed/Stories…),
     * sumando {@link #POST_VIEWS_KEY} de la red — camino seguro que no depende de un breakdown del API
     * (research/06 §3.5). Vacía si la red no tiene key de visualizaciones por post o no hay datos.
     */
    private List<ContentTypeShare> viewsByContentType(String platform, List<PostRow> rows,
                                                       Map<Long, Map<String, BigDecimal>> metricsByPost) {
        String viewsKey = POST_VIEWS_KEY.get(platform);
        if (viewsKey == null) {
            return List.of();
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (PostRow r : rows) {
            if (!platform.equals(r.platform())) {
                continue;
            }
            Map<String, BigDecimal> vals = metricsByPost.get(r.id());
            BigDecimal v = vals == null ? null : vals.get(viewsKey);
            if (v == null) {
                continue;
            }
            boolean story = r.ephemeral() || "STORY".equals(r.postType());
            totals.merge(displayType(r.postType(), story), v, BigDecimal::add);
        }
        BigDecimal grandTotal = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grandTotal.signum() == 0) {
            return List.of();
        }
        return totals.entrySet().stream()
                .map(e -> new ContentTypeShare(e.getKey(), clean(e.getValue()), pct(e.getValue(), grandTotal)))
                .sorted(Comparator.comparing(ContentTypeShare::views).reversed())
                .toList();
    }

    /** Demografía de una red a partir de las filas agregadas (research/06 §3.1); null si no hay ninguna. */
    private static Demographics demographics(List<DemographicRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, List<DemographicRow>> byType = rows.stream()
                .collect(Collectors.groupingBy(DemographicRow::breakdownType));
        Demographics demographics = new Demographics(
                segments(byType.get("CITY")), segments(byType.get("COUNTRY")),
                segments(byType.get("AGE")), segments(byType.get("GENDER")));
        return demographics.isEmpty() ? null : demographics;
    }

    private static List<Segment> segments(List<DemographicRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        BigDecimal total = rows.stream().map(DemographicRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream()
                .sorted(Comparator.comparing(DemographicRow::value).reversed())
                .map(r -> new Segment(demographicLabel(r.breakdownType(), r.breakdownValue()),
                        clean(r.value()), pct(r.value(), total)))
                .toList();
    }

    /** Traduce el valor crudo del breakdown de Meta a una etiqueta legible en español. */
    private static String demographicLabel(String breakdownType, String raw) {
        if (raw == null) {
            return "";
        }
        return switch (breakdownType) {
            case "GENDER" -> switch (raw.toUpperCase(Locale.ROOT)) {
                case "F" -> "Mujeres";
                case "M" -> "Hombres";
                case "U" -> "Sin especificar";
                default -> raw;
            };
            case "COUNTRY" -> countryName(raw);
            default -> raw;
        };
    }

    /** Código ISO 3166-1 alpha-2 (ej. {@code PY}) → nombre en español; el propio código si no resuelve. */
    private static String countryName(String isoCode) {
        if (isoCode == null || isoCode.length() != 2) {
            return isoCode;
        }
        try {
            Locale region = new Locale.Builder().setRegion(isoCode.toUpperCase(Locale.ROOT)).build();
            String name = region.getDisplayCountry(Locale.forLanguageTag("es"));
            return (name == null || name.isBlank() || name.equalsIgnoreCase(isoCode)) ? isoCode : name;
        } catch (RuntimeException e) {
            return isoCode;
        }
    }

    /** Ratio {@code part/total} (0..1, como {@code engagementRate}); null si {@code total} es 0. */
    private static BigDecimal pct(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0 || part == null) {
            return null;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Valor del KPI: los stocks (seguidores) usan el último; los flujos, el total del período. */
    private static BigDecimal kpiValue(SummaryMetric m) {
        boolean stock = m.metric().endsWith("follower_count") || m.metric().endsWith("followers_count");
        BigDecimal v = stock ? m.latest() : m.total();
        return v == null ? BigDecimal.ZERO : v;
    }

    private ReachEvolution reachEvolution(String platform, PlatformSummary cur, PlatformSummary prev,
                                          Set<PlatformMetricKey> prevBaseline) {
        String reachKey = ACCOUNT_REACH_KEY.get(platform);
        if (reachKey == null) {
            return null;
        }
        BigDecimal curReach = metricTotal(cur, reachKey);
        if (curReach == null) {
            return null;
        }
        // Sin baseline real del alcance en el previo → previous/deltaPct null (no se inventa evolución).
        boolean hasBaseline = prevBaseline.contains(new PlatformMetricKey(platform, reachKey));
        BigDecimal prevReach = (prev == null || !hasBaseline) ? null : metricTotal(prev, reachKey);
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
        Map<String, BigDecimal> vals = metricsByPost.get(r.id());
        if (resolved != null) {
            metricKey = resolved.key();
            metricName = resolved.displayName();
            value = vals == null ? null : vals.get(metricKey);
        }
        BigDecimal watchTime = watchTimeSeconds(r.platform(), r.postType(), vals);
        // En el bare guardamos el remoto del post como candidato; enrich() decide cache vs remoto.
        return new ReportPost(
                r.id(), r.platform(), r.postType(), displayType(r.postType(), story),
                r.publishedAt(), localDate(r.publishedAt(), zone), r.permalink(), r.caption(),
                null, r.remoteThumbnailUrl(), story, metricKey, metricName, clean(value), watchTime);
    }

    /** Tiempo medio de visionado del post (sólo reels; research/06 §1); null si no aplica o no se capturó. */
    private static BigDecimal watchTimeSeconds(String platform, String postType, Map<String, BigDecimal> vals) {
        if (!"REEL".equals(postType) || vals == null) {
            return null;
        }
        String key = WATCH_TIME_KEY.get(platform);
        return key == null ? null : clean(vals.get(key));
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
            grouped.computeIfAbsent(p.platform() + " " + p.displayType(), k -> new ArrayList<>()).add(p);
        }
        return grouped.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split(" ", 2);
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

    /** Alcance resuelto del reporte: redes a incluir + cuentas a las que restringir ({@code null} = todas). */
    private record Scope(List<String> platforms, List<Long> accountIds) {
    }

    /**
     * Resuelve el alcance: si vienen {@code accountIds}, el reporte se arma SÓLO con esas cuentas —
     * deben pertenecer al cliente (multi-tenant; si no, 404) — y la red se deriva de ellas, con
     * prioridad sobre {@code platforms}. Sin {@code accountIds}, comportamiento por red (compat) y
     * {@code accountIds = null} (sin restricción de cuenta en las queries).
     */
    private Scope resolveScope(Long clientId, List<String> requestedPlatforms, List<Long> requestedAccountIds) {
        if (requestedAccountIds == null || requestedAccountIds.isEmpty()) {
            return new Scope(resolvePlatforms(clientId, requestedPlatforms), null);
        }
        Map<Long, SocialAccount> owned = new HashMap<>();
        for (SocialAccount account : accountRepository.findByClientId(clientId)) {
            owned.put(account.getId(), account);
        }
        List<Long> ids = new ArrayList<>();
        List<String> platforms = new ArrayList<>();
        for (Long id : requestedAccountIds) {
            if (id == null || ids.contains(id)) {
                continue;
            }
            SocialAccount account = owned.get(id);
            if (account == null) {
                throw ApiException.notFound(
                        "La cuenta %d no pertenece al cliente %d".formatted(id, clientId));
            }
            ids.add(id);
            String platform = account.getPlatform().name();
            if (!platforms.contains(platform)) {
                platforms.add(platform);
            }
        }
        if (ids.isEmpty()) {
            // accountIds traía sólo nulls/duplicados vacíos: trátalo como sin filtro (por red).
            return new Scope(resolvePlatforms(clientId, requestedPlatforms), null);
        }
        platforms.sort(Comparator.naturalOrder());
        return new Scope(platforms, ids);
    }

    /** KPIs por red: account-scoped si hay {@code accountIds}, por red (todas las cuentas) si no. */
    private SummaryResponse summary(Long clientId, LocalDate from, LocalDate to, List<Long> accountIds) {
        return accountIds == null
                ? summaryService.summary(clientId, from, to, null)
                : summaryService.summaryForAccounts(clientId, from, to, accountIds);
    }

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
