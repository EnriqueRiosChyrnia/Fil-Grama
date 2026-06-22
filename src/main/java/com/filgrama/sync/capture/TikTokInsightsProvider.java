package com.filgrama.sync.capture;

import java.math.BigDecimal;
import java.time.Instant;
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

import tools.jackson.databind.JsonNode;

/**
 * Provider real de TikTok (Display API): {@code GET /v2/user/info/} (cuenta),
 * {@code POST /v2/video/list/} (lista de videos) y {@code POST /v2/video/query/} (insights por
 * video). Pide solo los campos CORE del catálogo (spec/05) y devuelve crudo + {@code metric_key}.
 * El access token va en el header {@code Authorization: Bearer}.
 *
 * <p>Activo fuera de {@code local}/{@code test} (dev/CI usan {@link MockInsightsProvider}). TikTok
 * responde un envelope {@code {data, error:{code,...}}}: {@code code="ok"} es éxito; un
 * {@code rate_limit*} se trata como transitorio (lo reintenta el {@code Retrier} del job) y el resto
 * como terminal de la cuenta.
 *
 * <p><b>Límite del Display API</b> (spec/05): reach/watch-time/tráfico no salen de acá → fuera del v1.
 */
@Component
@Profile("!local & !test")
public class TikTokInsightsProvider implements InsightsProvider {

    private static final String API_BASE = "https://open.tiktokapis.com";
    private static final String VIDEO_FIELDS = "id,title,cover_image_url,share_url,create_time,"
            + "view_count,like_count,comment_count,share_count";

    private final RestClient http;

    public TikTokInsightsProvider() {
        this(InsightsHttpSupport.builder(API_BASE));
    }

    /** Visible para tests: {@link RestClient} apuntado a un servidor HTTP mock. */
    TikTokInsightsProvider(RestClient.Builder builder) {
        this.http = builder.build();
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.TIKTOK;
    }

    @Override
    public AccountCapture fetchAccountInsights(SocialAccount account, String accessToken) {
        String body = InsightsHttpSupport.get(http,
                "/v2/user/info/?fields=follower_count,likes_count,video_count", accessToken).getBody();
        JsonNode user = checked(body).path("data").path("user");

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "tt_follower_count", InsightsHttpSupport.number(user, "follower_count"));
        put(metrics, "tt_likes_count", InsightsHttpSupport.number(user, "likes_count"));
        put(metrics, "tt_video_count", InsightsHttpSupport.number(user, "video_count"));
        return new AccountCapture("GET /v2/user/info/", safe(body), metrics);
    }

    @Override
    public PostsListCapture fetchPosts(SocialAccount account, String accessToken) {
        String body = InsightsHttpSupport.postJson(http,
                "/v2/video/list/?fields=" + VIDEO_FIELDS, "{\"max_count\":20}", accessToken).getBody();

        List<RawPost> posts = new ArrayList<>();
        for (JsonNode video : checked(body).path("data").path("videos")) {
            String id = text(video, "id");
            if (id == null) {
                continue;
            }
            posts.add(new RawPost(id, PostType.VIDEO, text(video, "share_url"), text(video, "title"),
                    null, text(video, "cover_image_url"), epoch(video), false, null));
        }
        return new PostsListCapture("POST /v2/video/list/", safe(body), posts);
    }

    @Override
    public PostInsightsCapture fetchPostInsights(SocialAccount account, RawPost post, String accessToken) {
        String requestBody = "{\"filters\":{\"video_ids\":[\"" + jsonEscape(post.externalPostId()) + "\"]}}";
        String body = InsightsHttpSupport.postJson(http,
                "/v2/video/query/?fields=" + VIDEO_FIELDS, requestBody, accessToken).getBody();

        JsonNode video = firstVideo(checked(body));
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        put(metrics, "tt_view_count", InsightsHttpSupport.number(video, "view_count"));
        put(metrics, "tt_like_count", InsightsHttpSupport.number(video, "like_count"));
        put(metrics, "tt_comment_count", InsightsHttpSupport.number(video, "comment_count"));
        put(metrics, "tt_share_count", InsightsHttpSupport.number(video, "share_count"));
        return new PostInsightsCapture("POST /v2/video/query/", safe(body), metrics);
    }

    // ---- helpers ----

    /** Parsea el body y valida el envelope de error de TikTok; devuelve el árbol. */
    private JsonNode checked(String body) {
        JsonNode tree = InsightsHttpSupport.tree(body);
        String code = tree.path("error").path("code").asText();
        if (code == null || code.isBlank() || "ok".equalsIgnoreCase(code)) {
            return tree;
        }
        if (code.toLowerCase().contains("rate_limit")) {
            throw new TransientInsightsException("TikTok rate limit (" + code + ")");
        }
        throw new InsightsException("TikTok rechazó la consulta de insights (" + code + ")");
    }

    private JsonNode firstVideo(JsonNode tree) {
        for (JsonNode video : tree.path("data").path("videos")) {
            return video;
        }
        return InsightsHttpSupport.tree("{}");
    }

    private static Instant epoch(JsonNode video) {
        BigDecimal seconds = InsightsHttpSupport.number(video, "create_time");
        return seconds == null ? null : Instant.ofEpochSecond(seconds.longValue());
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

    private static String safe(String body) {
        return body == null || body.isBlank() ? "{}" : body;
    }

    private static String jsonEscape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
