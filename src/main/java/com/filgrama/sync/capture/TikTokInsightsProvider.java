package com.filgrama.sync.capture;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;

/**
 * Provider real de TikTok (Display API): {@code /v2/user/info/}, {@code /v2/video/list/},
 * {@code /v2/video/query/}. <b>Scaffolding</b>: sin App Review se usa {@link MockInsightsProvider}
 * (este bean no se carga por {@code @Profile}). Campos según spec/05.
 *
 * <p>TODO(F): rate limit ~600 req/min por endpoint; reach/watch-time fuera del Display API (v1).
 */
@Component
@Profile("!local & !test")
public class TikTokInsightsProvider implements InsightsProvider {

    private static final String NOT_READY =
            "TikTokInsightsProvider: pendiente de App Review; sin credenciales reales no se ejercita.";

    private final RestClient client;

    public TikTokInsightsProvider() {
        this.client = RestClient.builder().baseUrl("https://open.tiktokapis.com").build();
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.TIKTOK;
    }

    @Override
    public AccountCapture fetchAccountInsights(SocialAccount account, String accessToken) {
        throw new UnsupportedOperationException(NOT_READY);
    }

    @Override
    public PostsListCapture fetchPosts(SocialAccount account, String accessToken) {
        throw new UnsupportedOperationException(NOT_READY);
    }

    @Override
    public PostInsightsCapture fetchPostInsights(SocialAccount account, RawPost post, String accessToken) {
        throw new UnsupportedOperationException(NOT_READY);
    }
}
