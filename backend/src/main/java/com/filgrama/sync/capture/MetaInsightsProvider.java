package com.filgrama.sync.capture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.AccountReachSeriesCapture;
import com.filgrama.sync.capture.dto.AudienceDemographicsCapture;
import com.filgrama.sync.capture.dto.DatedValue;
import com.filgrama.sync.capture.dto.DemographicSegment;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;
import com.filgrama.sync.capture.dto.StoryCapture;

import tools.jackson.databind.JsonNode;

/**
 * Provider real de Meta (Instagram + Facebook) sobre el Graph API. Pide solo los campos de las
 * métricas <b>CORE</b> del catálogo (spec/05) y devuelve el <b>payload crudo</b> + el mapa
 * {@code metric_key -> value} que consume el deriver. Robustez por {@link InsightsHttpSupport}
 * (timeouts, clasificación transitorio/terminal, autorregulación con {@code X-App-Usage}).
 *
 * <p>Activo fuera de {@code local}/{@code test} (dev/CI usan {@link MockInsightsProvider}). El
 * {@code externalAccountId} de la cuenta es el nodo Graph a consultar: IG user id (IG) o Page id (FB);
 * el token es el Page token derivado en el canje.
 *
 * <p><b>Pendiente de validación en sandbox</b> (spec/05 lo pide explícito; estas APIs cambian):
 * {@code fb_page_views} (métrica {@code views}/{@code page_media_view}) y {@code fb_post_views}
 * quedan fuera del set pedido para no arriesgar que un nombre inválido tumbe toda la llamada de
 * insights; se activan tras confirmar el nombre exacto contra una Página real.
 */
@Component
@Profile("!local & !test")
public class MetaInsightsProvider implements InsightsProvider {

    private static final String GRAPH_BASE = "https://graph.facebook.com";
    private static final String V = "/v21.0";
    /** Formato de fecha de Meta: ISO-8601 con offset sin dos puntos, ej. {@code 2026-06-01T12:00:00+0000}. */
    private static final DateTimeFormatter META_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final RestClient http;

    public MetaInsightsProvider() {
        this(InsightsHttpSupport.builder(GRAPH_BASE));
    }

    /** Visible para tests: {@link RestClient} apuntado a un servidor HTTP mock. */
    MetaInsightsProvider(RestClient.Builder builder) {
        this.http = builder.build();
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK;
    }

    // ---- cuenta ----

    @Override
    public AccountCapture fetchAccountInsights(SocialAccount account, String accessToken,
            LocalDate windowSince, LocalDate windowUntil) {
        return account.getPlatform() == Platform.INSTAGRAM
                ? igAccount(account, accessToken, windowSince, windowUntil)
                : fbAccount(account, accessToken);
    }

    /**
     * {@code views}/{@code total_interactions}/{@code accounts_engaged} como {@code total_value} de
     * la ventana {@code [since, until]} (~30 días, FG-CS-CAP): pedidas por día único daban 0 en días
     * sin actividad (research/06 §9). {@code reach} se saca de acá: es {@code time_series} propia
     * (ver {@link #fetchAccountReachSeries}). {@code followers_count} es una foto del nodo, no un
     * insight — no depende de ventana.
     */
    private AccountCapture igAccount(SocialAccount account, String token, LocalDate since, LocalDate until) {
        String id = enc(account.getExternalAccountId());
        String nodeBody = graphGet("/" + id + "?fields=followers_count,username&access_token=" + enc(token));
        String insightsBody = graphGet("/" + id + "/insights"
                + "?metric=views,total_interactions,accounts_engaged"
                + "&period=day&metric_type=total_value&since=" + since + "&until=" + until
                + "&access_token=" + enc(token));

        Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(insightsBody));
        JsonNode node = InsightsHttpSupport.tree(nodeBody);

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "ig_followers_count", InsightsHttpSupport.number(node, "followers_count"));
        put(metrics, "ig_views", insights.get("views"));
        put(metrics, "ig_total_interactions", insights.get("total_interactions"));
        put(metrics, "ig_accounts_engaged", insights.get("accounts_engaged"));
        return new AccountCapture("GET /" + V + "/{ig-user-id} + /insights",
                combine("node", nodeBody, "insights", insightsBody), metrics);
    }

    /**
     * {@code reach} como {@code time_series} de {@code [since, until]} (~30 días): la única métrica
     * de cuenta con serie propia en Meta (spec/05). Una fila por día devuelto ({@code values[].end_time}
     * → fecha UTC). El mismo pedido corre en <b>todas</b> las corridas (job diario y scan al conectar),
     * así el primer sync de una cuenta nueva ya "backfillea" sus últimos ~30 días (FG-CS-CAP #1) y las
     * corridas siguientes corrigen la ventana por el delay de hasta 48h de Meta (spec/10).
     */
    @Override
    public AccountReachSeriesCapture fetchAccountReachSeries(SocialAccount account, String accessToken,
            LocalDate since, LocalDate until) {
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return new AccountReachSeriesCapture(null, null, List.of());
        }
        String id = enc(account.getExternalAccountId());
        String body = graphGet("/" + id + "/insights"
                + "?metric=reach&period=day&metric_type=time_series&since=" + since + "&until=" + until
                + "&access_token=" + enc(accessToken));

        List<DatedValue> values = new ArrayList<>();
        JsonNode tree = InsightsHttpSupport.tree(body);
        for (JsonNode metric : tree.path("data")) {
            if (!"reach".equals(text(metric, "name"))) {
                continue;
            }
            for (JsonNode point : metric.path("values")) {
                Instant endTime = parseTime(text(point, "end_time"));
                BigDecimal value = InsightsHttpSupport.number(point, "value");
                if (endTime != null && value != null) {
                    values.add(new DatedValue(endTime.atZone(ZoneOffset.UTC).toLocalDate(), value));
                }
            }
        }
        return new AccountReachSeriesCapture("GET /" + V + "/{ig-user-id}/insights (reach time_series)",
                body, values);
    }

    private AccountCapture fbAccount(SocialAccount account, String token) {
        String id = enc(account.getExternalAccountId());
        // page_views/views queda fuera hasta validar el nombre en sandbox (ver javadoc de clase).
        String body = graphGet("/" + id + "/insights"
                + "?metric=page_views_total,page_post_engagements,page_fan_adds"
                + "&period=day&access_token=" + enc(token));

        Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(body));
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "fb_page_views_total", insights.get("page_views_total"));
        put(metrics, "fb_page_post_engagements", insights.get("page_post_engagements"));
        put(metrics, "fb_page_fan_adds", insights.get("page_fan_adds"));
        return new AccountCapture("GET /" + V + "/{page-id}/insights", body, metrics);
    }

    // ---- lista de posts ----

    @Override
    public PostsListCapture fetchPosts(SocialAccount account, String accessToken) {
        String id = enc(account.getExternalAccountId());
        boolean ig = account.getPlatform() == Platform.INSTAGRAM;
        String body = ig
                ? graphGet("/" + id + "/media"
                        + "?fields=id,caption,media_type,media_product_type,permalink,media_url,thumbnail_url,timestamp"
                        + "&limit=25&access_token=" + enc(accessToken))
                : graphGet("/" + id + "/posts"
                        + "?fields=id,message,permalink_url,created_time,full_picture"
                        + "&limit=25&access_token=" + enc(accessToken));

        List<RawPost> posts = new ArrayList<>();
        for (JsonNode item : InsightsHttpSupport.tree(body).path("data")) {
            posts.add(ig ? igPost(item) : fbPost(item));
        }
        return new PostsListCapture(ig ? "GET /" + V + "/{ig-user-id}/media"
                : "GET /" + V + "/{page-id}/posts", body, posts);
    }

    private RawPost igPost(JsonNode item) {
        return new RawPost(
                text(item, "id"), igPostType(item), text(item, "permalink"), text(item, "caption"),
                text(item, "media_url"), text(item, "thumbnail_url"),
                parseTime(text(item, "timestamp")), false, null);
    }

    private RawPost fbPost(JsonNode item) {
        // El tipo de un post de Página no sale fiable de /posts → IMAGE por defecto (afinar en sandbox).
        return new RawPost(
                text(item, "id"), PostType.IMAGE, text(item, "permalink_url"), text(item, "message"),
                text(item, "full_picture"), text(item, "full_picture"),
                parseTime(text(item, "created_time")), false, null);
    }

    private PostType igPostType(JsonNode item) {
        String product = upper(text(item, "media_product_type"));
        if ("REELS".equals(product)) {
            return PostType.REEL;
        }
        if ("STORY".equals(product)) {
            return PostType.STORY;
        }
        return switch (upper(text(item, "media_type"))) {
            case "VIDEO" -> PostType.VIDEO;
            case "CAROUSEL_ALBUM" -> PostType.CAROUSEL;
            default -> PostType.IMAGE;
        };
    }

    // ---- insights por post ----

    @Override
    public PostInsightsCapture fetchPostInsights(SocialAccount account, RawPost post, String accessToken) {
        return account.getPlatform() == Platform.INSTAGRAM
                ? igPostInsights(post, accessToken)
                : fbPostInsights(post, accessToken);
    }

    private PostInsightsCapture igPostInsights(RawPost post, String token) {
        String id = enc(post.externalPostId());
        // likes/comments son campos del nodo (no métricas de /insights).
        String nodeBody = graphGet("/" + id + "?fields=like_count,comments_count&access_token=" + enc(token));
        String insightsBody = graphGet("/" + id + "/insights"
                + "?metric=reach,views,saved,shares,total_interactions&access_token=" + enc(token));

        JsonNode node = InsightsHttpSupport.tree(nodeBody);
        Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(insightsBody));
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "ig_post_reach", insights.get("reach"));
        put(metrics, "ig_post_views", insights.get("views"));
        put(metrics, "ig_post_likes", InsightsHttpSupport.number(node, "like_count"));
        put(metrics, "ig_post_comments", InsightsHttpSupport.number(node, "comments_count"));
        put(metrics, "ig_post_saved", insights.get("saved"));
        put(metrics, "ig_post_shares", insights.get("shares"));
        put(metrics, "ig_post_total_interactions", insights.get("total_interactions"));
        return new PostInsightsCapture("GET /" + V + "/{ig-media-id} + /insights",
                combine("node", nodeBody, "insights", insightsBody), metrics);
    }

    private PostInsightsCapture fbPostInsights(RawPost post, String token) {
        String id = enc(post.externalPostId());
        String body = graphGet("/" + id + "/insights"
                + "?metric=post_engaged_users,post_clicks,post_reactions_by_type_total,post_video_views"
                + "&access_token=" + enc(token));

        Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(body));
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "fb_post_engaged_users", insights.get("post_engaged_users"));
        put(metrics, "fb_post_clicks", insights.get("post_clicks"));
        put(metrics, "fb_post_reactions_total", insights.get("post_reactions_by_type_total"));
        put(metrics, "fb_post_video_views", insights.get("post_video_views"));
        return new PostInsightsCapture("GET /" + V + "/{post-id}/insights", body, metrics);
    }

    // ---- stories (solo IG) ----

    @Override
    public List<StoryCapture> fetchStories(SocialAccount account, String accessToken) {
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return List.of();
        }
        String id = enc(account.getExternalAccountId());
        String listBody = graphGet("/" + id + "/stories"
                + "?fields=id,media_type,permalink,timestamp,thumbnail_url,media_url&access_token=" + enc(accessToken));

        List<StoryCapture> stories = new ArrayList<>();
        for (JsonNode item : InsightsHttpSupport.tree(listBody).path("data")) {
            String storyId = text(item, "id");
            if (storyId == null) {
                continue;
            }
            Instant published = parseTime(text(item, "timestamp"));
            RawPost meta = new RawPost(storyId, PostType.STORY, text(item, "permalink"), null,
                    text(item, "media_url"), text(item, "thumbnail_url"), published, true,
                    published == null ? null : published.plus(24, ChronoUnit.HOURS));

            String insightsBody = graphGet("/" + enc(storyId) + "/insights"
                    + "?metric=reach,replies&access_token=" + enc(accessToken));
            Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(insightsBody));
            Map<String, BigDecimal> metrics = new LinkedHashMap<>();
            put(metrics, "ig_story_reach", insights.get("reach"));
            put(metrics, "ig_story_replies", insights.get("replies"));
            // La miniatura la cachea el track E desde remoteThumbnailUrl; acá no bajamos los bytes.
            stories.add(new StoryCapture(meta, "GET /" + V + "/{story-id}/insights", insightsBody,
                    metrics, null, null));
        }
        return stories;
    }

    // ---- extras v1.1 (FG-T1): llamadas Graph aparte y best-effort ----

    /**
     * {@code follows_and_unfollows}, splits {@code follow_type} de views/reach, {@code profile_views}
     * y taps por destino. Cada sub-llamada es independiente y best-effort ({@link #graphGetQuietly}):
     * un nombre/breakdown que la API rechace devuelve {@code null} y se omite, sin tumbar las demás
     * ni la captura CORE. Mismo rango {@code [since, until]} que {@link #fetchAccountInsights}: son
     * {@code total_value} de período, no de un día (FG-CS-CAP #1 y #2 — research/06 §9 encontró estas
     * llamadas devolviendo vacío con {@code period=day} solo). Solo IG.
     */
    @Override
    public AccountCapture fetchAccountExtras(SocialAccount account, String token, LocalDate since, LocalDate until) {
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return new AccountCapture(null, null, Map.of());
        }
        String id = enc(account.getExternalAccountId());
        String range = "&since=" + since + "&until=" + until;
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        Map<String, String> raws = new LinkedHashMap<>();

        String followsAndUnfollows = graphGetQuietly("/" + id + "/insights"
                + "?metric=follows_and_unfollows&period=day&metric_type=total_value" + range
                + "&access_token=" + enc(token));
        if (followsAndUnfollows != null) {
            raws.put("follows_and_unfollows", followsAndUnfollows);
            put(metrics, "ig_follows_and_unfollows",
                    insightsByName(InsightsHttpSupport.tree(followsAndUnfollows)).get("follows_and_unfollows"));
        }

        String profileViews = graphGetQuietly("/" + id + "/insights"
                + "?metric=profile_views&period=day&metric_type=total_value" + range
                + "&access_token=" + enc(token));
        if (profileViews != null) {
            raws.put("profile_views", profileViews);
            put(metrics, "ig_profile_views", insightsByName(InsightsHttpSupport.tree(profileViews)).get("profile_views"));
        }

        String viewsSplit = graphGetQuietly("/" + id + "/insights"
                + "?metric=views&period=day&metric_type=total_value&breakdown=follow_type" + range
                + "&access_token=" + enc(token));
        if (viewsSplit != null) {
            raws.put("views_follow_type", viewsSplit);
            forEachBreakdown(InsightsHttpSupport.tree(viewsSplit), (dim, val) ->
                    put(metrics, isNonFollower(dim) ? "ig_views_non_followers" : "ig_views_followers", val));
        }

        String reachSplit = graphGetQuietly("/" + id + "/insights"
                + "?metric=reach&period=day&metric_type=total_value&breakdown=follow_type" + range
                + "&access_token=" + enc(token));
        if (reachSplit != null) {
            raws.put("reach_follow_type", reachSplit);
            forEachBreakdown(InsightsHttpSupport.tree(reachSplit), (dim, val) ->
                    put(metrics, isNonFollower(dim) ? "ig_reach_non_followers" : "ig_reach_followers", val));
        }

        String taps = graphGetQuietly("/" + id + "/insights"
                + "?metric=profile_links_taps&period=day&metric_type=total_value&breakdown=contact_button_type"
                + range + "&access_token=" + enc(token));
        if (taps != null) {
            raws.put("profile_links_taps", taps);
            BigDecimal[] total = {null};
            forEachBreakdown(InsightsHttpSupport.tree(taps), (dim, val) -> {
                total[0] = total[0] == null ? val : total[0].add(val);
                String key = tapsKey(dim);
                if (key != null) {
                    put(metrics, key, val);
                }
            });
            put(metrics, "ig_profile_links_taps", total[0]);
        }

        return new AccountCapture("GET /" + V + "/{ig-user-id}/insights (extras v1.1)", combineAll(raws), metrics);
    }

    /**
     * Métricas extra por media: {@code reposts}, {@code profile_visits} y (solo reels)
     * {@code ig_reels_avg_watch_time} (la API la da en ms → se persiste en segundos). Dos llamadas
     * <b>separadas</b> (no una combinada, FG-CS-CAP #3): así un nombre rechazado en una no arrastra a
     * la otra — research/06 §9 encontró el reel con {@code reposts}/{@code profile_visits} capturados
     * pero sin watch-time pese a estar cableado; aislar la sub-llamada lo hace diagnosticable por
     * separado en {@code raw_api_payloads} sin arriesgar las otras dos. Best-effort, no rompe
     * {@link #fetchPostInsights}. Solo IG.
     */
    @Override
    public PostInsightsCapture fetchPostExtras(SocialAccount account, RawPost post, String token) {
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return new PostInsightsCapture(null, null, Map.of());
        }
        String id = enc(post.externalPostId());
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        Map<String, String> raws = new LinkedHashMap<>();

        String base = graphGetQuietly("/" + id + "/insights?metric=reposts,profile_visits&access_token=" + enc(token));
        if (base != null) {
            raws.put("reposts_profile_visits", base);
            Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(base));
            put(metrics, "ig_post_reposts", insights.get("reposts"));
            put(metrics, "ig_post_profile_visits", insights.get("profile_visits"));
        }

        if (post.postType() == PostType.REEL) {
            String watch = graphGetQuietly("/" + id
                    + "/insights?metric=ig_reels_avg_watch_time&access_token=" + enc(token));
            if (watch != null) {
                raws.put("ig_reels_avg_watch_time", watch);
                BigDecimal watchMs = insightsByName(InsightsHttpSupport.tree(watch)).get("ig_reels_avg_watch_time");
                if (watchMs != null) {
                    metrics.put("ig_reels_avg_watch_time",
                            watchMs.divide(BigDecimal.valueOf(1000), 3, java.math.RoundingMode.HALF_UP));
                }
            }
        }

        if (raws.isEmpty()) {
            return new PostInsightsCapture(null, null, Map.of());
        }
        return new PostInsightsCapture("GET /" + V + "/{ig-media-id}/insights (extras v1.1)",
                combineAll(raws), metrics);
    }

    /**
     * Demografía de seguidores (lifetime, requiere ≥100 seguidores). Una sub-llamada best-effort por
     * breakdown (age/gender/city/country); cada segmento → una fila de {@code audience_demographics}.
     * Solo IG en v1.1.
     */
    @Override
    public AudienceDemographicsCapture fetchAudienceDemographics(SocialAccount account, String token) {
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return new AudienceDemographicsCapture(null, null, List.of());
        }
        String id = enc(account.getExternalAccountId());
        List<DemographicSegment> segments = new ArrayList<>();
        Map<String, String> raws = new LinkedHashMap<>();
        for (String[] bd : new String[][] {{"age", "AGE"}, {"gender", "GENDER"}, {"city", "CITY"}, {"country", "COUNTRY"}}) {
            String body = graphGetQuietly("/" + id + "/insights"
                    + "?metric=follower_demographics&period=lifetime&metric_type=total_value&breakdown=" + bd[0]
                    + "&access_token=" + enc(token));
            if (body == null) {
                continue;
            }
            raws.put("follower_demographics_" + bd[0], body);
            forEachBreakdown(InsightsHttpSupport.tree(body), (dim, val) ->
                    segments.add(new DemographicSegment("FOLLOWER", bd[1], dim, val)));
        }
        return new AudienceDemographicsCapture("GET /" + V + "/{ig-user-id}/insights (follower_demographics)",
                combineAll(raws), segments);
    }

    // ---- helpers ----

    /** {@code path} relativo (con {@code /}) → URL absoluta versionada del Graph API. */
    private String graphGet(String path) {
        var response = InsightsHttpSupport.get(http, GRAPH_BASE + V + path, null);
        InsightsHttpSupport.throttleOnUsage(response.getHeaders());
        return response.getBody() == null ? "{}" : response.getBody();
    }

    /** GET best-effort: devuelve el body, o {@code null} si la llamada falla (no propaga el error). */
    private String graphGetQuietly(String path) {
        try {
            return graphGet(path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Recorre {@code data[].total_value.breakdowns[].results[]} de un insight con breakdown y entrega
     * al sink {@code (primer dimension_value, value)} de cada segmento con valor numérico.
     */
    private void forEachBreakdown(JsonNode tree, java.util.function.BiConsumer<String, BigDecimal> sink) {
        for (JsonNode metric : tree.path("data")) {
            for (JsonNode breakdown : metric.path("total_value").path("breakdowns")) {
                for (JsonNode result : breakdown.path("results")) {
                    JsonNode dims = result.path("dimension_values");
                    if (!dims.isArray() || dims.size() == 0) {
                        continue;
                    }
                    String dim = dims.get(0).asText();
                    BigDecimal value = InsightsHttpSupport.number(result, "value");
                    if (dim != null && !dim.isBlank() && value != null) {
                        sink.accept(dim, value);
                    }
                }
            }
        }
    }

    private static boolean isNonFollower(String dim) {
        return upper(dim).contains("NON");
    }

    /** Mapea el {@code contact_button_type} a la {@code metric_key} de tap por destino, o {@code null}. */
    private static String tapsKey(String dim) {
        String d = upper(dim);
        if (d.contains("WHATSAPP")) {
            return "ig_taps_whatsapp";
        }
        if (d.contains("DIRECTION")) {
            return "ig_taps_direction";
        }
        return null;
    }

    /** Combina varias respuestas crudas ({@code clave -> json}) en un solo objeto crudo; {@code null} si no hay ninguna. */
    private static String combineAll(Map<String, String> raws) {
        if (raws.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : raws.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":").append(safe(e.getValue()));
        }
        return sb.append('}').toString();
    }

    /** {@code data[]} de Meta insights → {nombre de métrica de la API → valor}. */
    private Map<String, BigDecimal> insightsByName(JsonNode tree) {
        Map<String, BigDecimal> byName = new LinkedHashMap<>();
        for (JsonNode metric : tree.path("data")) {
            String name = text(metric, "name");
            BigDecimal value = InsightsHttpSupport.insightValue(metric);
            if (name != null && value != null) {
                byName.put(name, value);
            }
        }
        return byName;
    }

    private static void put(Map<String, BigDecimal> metrics, String key, BigDecimal value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(java.util.Locale.ROOT);
    }

    private static Instant parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw, META_TIME).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ignore) {
                return null;
            }
        }
    }

    /** Combina dos respuestas crudas (ambas JSON) en un solo objeto crudo, sin re-serializar. */
    private static String combine(String k1, String b1, String k2, String b2) {
        return "{\"" + k1 + "\":" + safe(b1) + ",\"" + k2 + "\":" + safe(b2) + "}";
    }

    private static String safe(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private static String enc(String raw) {
        return raw == null ? "" : java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
}
