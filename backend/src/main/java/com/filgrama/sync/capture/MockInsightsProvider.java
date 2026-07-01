package com.filgrama.sync.capture;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.AccountReachSeriesCapture;
import com.filgrama.sync.capture.dto.DatedValue;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;
import com.filgrama.sync.capture.dto.StoryCapture;

/**
 * Provider fake determinista para {@code local}/{@code test}: corre el pipeline diario end-to-end
 * sin tocar APIs reales. Las {@code metric_key} y posts son estables para una misma cuenta, así un
 * re-run del mismo día no inventa filas nuevas (idempotencia). El {@link #setSeed(long) seed}
 * desplaza todos los valores: subirlo entre corridas simula que la API devolvió números nuevos
 * (para probar "último valor del día gana").
 *
 * <p>Sentinela de fallos: si el {@code handle} de la cuenta contiene {@code "boom"} lanza
 * {@link InsightsException} en el primer paso → la cuenta queda {@code ERROR} y el job sigue.
 *
 * <p>No depende de Jackson: arma el crudo como texto JSON con contenido controlado.
 */
@Component
@Profile({"local", "test"})
public class MockInsightsProvider implements InsightsProvider {

    /** Epoch fijo para que las fechas de publicación sean deterministas (no usa el reloj). */
    private static final Instant BASE_PUBLISHED = Instant.parse("2026-06-01T12:00:00Z");
    private static final String FAIL_SENTINEL = "boom";

    private volatile long seed = 1_000L;

    /** Ajusta el desplazamiento base de todos los valores (para tests de actualización del día). */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    @Override
    public boolean supports(Platform platform) {
        return true;
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public AccountCapture fetchAccountInsights(SocialAccount account, String accessToken,
            LocalDate windowSince, LocalDate windowUntil) {
        guard(account);
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        for (String key : accountMetricKeys(account.getPlatform())) {
            metrics.put(key, value(key, account.getExternalAccountId()));
        }
        Map<String, Object> raw = new LinkedHashMap<>(metrics);
        raw.put("_mock_account", account.getExternalAccountId());
        return new AccountCapture("mock:" + account.getPlatform() + ":account_insights", toJson(raw), metrics);
    }

    /** {@code ig_reach} determinista, un valor por día de {@code [since, until]} (simula el time_series real). */
    @Override
    public AccountReachSeriesCapture fetchAccountReachSeries(SocialAccount account, String accessToken,
            LocalDate since, LocalDate until) {
        guard(account);
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return new AccountReachSeriesCapture(null, null, List.of());
        }
        List<DatedValue> values = new ArrayList<>();
        Map<String, Object> raw = new LinkedHashMap<>();
        for (LocalDate day = since; !day.isAfter(until); day = day.plusDays(1)) {
            BigDecimal v = value("ig_reach", account.getExternalAccountId() + "|" + day);
            values.add(new DatedValue(day, v));
            raw.put(day.toString(), v);
        }
        return new AccountReachSeriesCapture("mock:" + account.getPlatform() + ":reach_time_series",
                toJson(raw), values);
    }

    @Override
    public PostsListCapture fetchPosts(SocialAccount account, String accessToken) {
        guard(account);
        List<RawPost> posts = mockPosts(account);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("_mock_posts", posts.size());
        for (int i = 0; i < posts.size(); i++) {
            raw.put("post_" + i, posts.get(i).externalPostId());
        }
        return new PostsListCapture("mock:" + account.getPlatform() + ":media_list", toJson(raw), posts);
    }

    @Override
    public PostInsightsCapture fetchPostInsights(SocialAccount account, RawPost post, String accessToken) {
        guard(account);
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        for (String key : postMetricKeys(account.getPlatform())) {
            metrics.put(key, value(key, post.externalPostId()));
        }
        Map<String, Object> raw = new LinkedHashMap<>(metrics);
        raw.put("_mock_post", post.externalPostId());
        return new PostInsightsCapture("mock:" + account.getPlatform() + ":post_insights", toJson(raw), metrics);
    }

    @Override
    public List<StoryCapture> fetchStories(SocialAccount account, String accessToken) {
        guard(account);
        if (account.getPlatform() != Platform.INSTAGRAM) {
            return List.of();
        }
        String externalId = account.getExternalAccountId() + "-story1";
        Instant published = BASE_PUBLISHED.plus(2, ChronoUnit.HOURS);
        RawPost meta = new RawPost(externalId, PostType.STORY,
                "https://instagram.com/stories/" + externalId, "mock story", null,
                "https://cdn.mock/thumb/" + externalId + ".jpg",
                published, true, published.plus(24, ChronoUnit.HOURS));
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("ig_story_reach", value("ig_story_reach", externalId));
        metrics.put("ig_story_replies", value("ig_story_replies", externalId));
        Map<String, Object> raw = new LinkedHashMap<>(metrics);
        raw.put("_mock_story", externalId);
        byte[] thumb = ("MOCK_THUMB_" + externalId).getBytes(StandardCharsets.UTF_8);
        return List.of(new StoryCapture(meta, "mock:IG:story_insights", toJson(raw), metrics, thumb, "image/jpeg"));
    }

    // ---- helpers deterministas ----

    private void guard(SocialAccount account) {
        if (account.getHandle() != null && account.getHandle().contains(FAIL_SENTINEL)) {
            throw new InsightsException("mock: fallo simulado de API para la cuenta " + account.getId());
        }
    }

    /** Valor estable por (key, ancla) desplazado por el seed; nunca negativo. */
    private BigDecimal value(String key, String anchor) {
        long base = Math.abs((long) (key + '|' + anchor).hashCode() % 1_000);
        return BigDecimal.valueOf(seed + base);
    }

    private List<RawPost> mockPosts(SocialAccount account) {
        String a = account.getExternalAccountId();
        return switch (account.getPlatform()) {
            case INSTAGRAM -> List.of(
                    post(a + "-p1", PostType.IMAGE, "feed"),
                    post(a + "-p2", PostType.REEL, "reel"));
            case FACEBOOK -> List.of(
                    post(a + "-p1", PostType.IMAGE, "feed"),
                    post(a + "-p2", PostType.VIDEO, "video"));
            case TIKTOK -> List.of(
                    post(a + "-v1", PostType.VIDEO, "video"),
                    post(a + "-v2", PostType.VIDEO, "video"));
        };
    }

    private RawPost post(String externalId, PostType type, String tag) {
        Instant published = BASE_PUBLISHED.minus(Math.abs(externalId.hashCode() % 30), ChronoUnit.DAYS);
        return new RawPost(externalId, type,
                "https://example.com/p/" + externalId, "mock " + tag + " caption",
                "https://cdn.mock/media/" + externalId, "https://cdn.mock/thumb/" + externalId + ".jpg",
                published, false, null);
    }

    /** {@code ig_reach} no va acá: es {@code time_series}, la sirve {@link #fetchAccountReachSeries}. */
    private List<String> accountMetricKeys(Platform platform) {
        return switch (platform) {
            case INSTAGRAM -> List.of("ig_followers_count", "ig_views",
                    "ig_total_interactions", "ig_accounts_engaged");
            case FACEBOOK -> List.of("fb_page_views_total", "fb_page_post_engagements",
                    "fb_page_fan_adds", "fb_page_views");
            case TIKTOK -> List.of("tt_follower_count", "tt_likes_count", "tt_video_count");
        };
    }

    private List<String> postMetricKeys(Platform platform) {
        return switch (platform) {
            case INSTAGRAM -> List.of("ig_post_reach", "ig_post_views", "ig_post_likes",
                    "ig_post_comments", "ig_post_saved", "ig_post_shares", "ig_post_total_interactions");
            case FACEBOOK -> List.of("fb_post_engaged_users", "fb_post_clicks",
                    "fb_post_reactions_total", "fb_post_video_views", "fb_post_views");
            case TIKTOK -> List.of("tt_view_count", "tt_like_count", "tt_comment_count", "tt_share_count");
        };
    }

    /** Serializa un objeto plano a texto JSON (contenido controlado: claves seguras, valores num/string). */
    private static String toJson(Map<String, ?> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof BigDecimal bd) {
                sb.append(bd.toPlainString());
            } else if (v instanceof Number n) {
                sb.append(n);
            } else {
                sb.append('"').append(escape(v.toString())).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
