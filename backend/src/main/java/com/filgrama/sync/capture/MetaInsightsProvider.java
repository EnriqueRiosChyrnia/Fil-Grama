package com.filgrama.sync.capture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
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
    public AccountCapture fetchAccountInsights(SocialAccount account, String accessToken) {
        return account.getPlatform() == Platform.INSTAGRAM
                ? igAccount(account, accessToken)
                : fbAccount(account, accessToken);
    }

    private AccountCapture igAccount(SocialAccount account, String token) {
        String id = enc(account.getExternalAccountId());
        String nodeBody = graphGet("/" + id + "?fields=followers_count,username&access_token=" + enc(token));
        String insightsBody = graphGet("/" + id + "/insights"
                + "?metric=reach,views,total_interactions,accounts_engaged"
                + "&period=day&metric_type=total_value&access_token=" + enc(token));

        Map<String, BigDecimal> insights = insightsByName(InsightsHttpSupport.tree(insightsBody));
        JsonNode node = InsightsHttpSupport.tree(nodeBody);

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "ig_followers_count", InsightsHttpSupport.number(node, "followers_count"));
        put(metrics, "ig_reach", insights.get("reach"));
        put(metrics, "ig_views", insights.get("views"));
        put(metrics, "ig_total_interactions", insights.get("total_interactions"));
        put(metrics, "ig_accounts_engaged", insights.get("accounts_engaged"));
        return new AccountCapture("GET /" + V + "/{ig-user-id} + /insights",
                combine("node", nodeBody, "insights", insightsBody), metrics);
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

    // ---- helpers ----

    /** {@code path} relativo (con {@code /}) → URL absoluta versionada del Graph API. */
    private String graphGet(String path) {
        var response = InsightsHttpSupport.get(http, GRAPH_BASE + V + path, null);
        InsightsHttpSupport.throttleOnUsage(response.getHeaders());
        return response.getBody() == null ? "{}" : response.getBody();
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
