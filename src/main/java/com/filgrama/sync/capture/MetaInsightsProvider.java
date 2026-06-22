package com.filgrama.sync.capture;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;
import com.filgrama.sync.capture.dto.StoryCapture;

/**
 * Provider real de Meta (Instagram + Facebook) sobre el Graph API. <b>Scaffolding</b>: la app
 * todavía no pasó App Review, así que en {@code local}/{@code test} se usa {@link MockInsightsProvider}
 * (este bean ni se carga por {@code @Profile}). Endpoints/campos según spec/05 + spec/10.
 *
 * <p>TODO(F): batching (hasta 50 ops/request) y autorregulación leyendo {@code X-App-Usage} /
 * {@code X-Business-Use-Case-Usage} antes del 429. Traducir api_field -> metric_key del catálogo.
 */
@Component
@Profile("!local & !test")
public class MetaInsightsProvider implements InsightsProvider {

    private static final String NOT_READY =
            "MetaInsightsProvider: pendiente de App Review; sin credenciales reales no se ejercita.";

    private final RestClient client;

    public MetaInsightsProvider() {
        // TODO(F): fijar versión del Graph API (ej. /v21.0) y timeouts por request.
        // Builder estático: no requiere un bean RestClient.Builder (que no existe en prod por defecto).
        this.client = RestClient.builder().baseUrl("https://graph.facebook.com").build();
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK;
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

    @Override
    public List<StoryCapture> fetchStories(SocialAccount account, String accessToken) {
        // TODO(F/CU8): /{ig-user-id}/stories + story_insights (webhook-first; polling como red de seguridad).
        throw new UnsupportedOperationException(NOT_READY);
    }
}
