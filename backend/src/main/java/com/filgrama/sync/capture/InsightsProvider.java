package com.filgrama.sync.capture;

import java.util.List;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.AudienceDemographicsCapture;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;
import com.filgrama.sync.capture.dto.StoryCapture;

/**
 * Abstracción mockeable de la API de insights de una red. Cada implementación devuelve el
 * <b>payload crudo</b> (para guardarlo append-only) más una vista normalizada
 * {@code metric_key -> value} que el derive intersecta con el catálogo CORE/ACTIVE.
 *
 * <p>Las implementaciones reales ({@code Meta}/{@code TikTok}) son scaffolding y no se ejercitan
 * sin App Review; {@link MockInsightsProvider} corre el pipeline end-to-end en {@code local}/{@code test}.
 *
 * <p>Errores transitorios (timeout/5xx/429) deben lanzarse como {@link TransientInsightsException}
 * para que el {@link com.filgrama.sync.job.Retrier} los reintente; los terminales como
 * {@link InsightsException}.
 */
public interface InsightsProvider {

    /** ¿Esta implementación atiende la red dada? */
    boolean supports(Platform platform);

    /** Insights a nivel cuenta (scope {@code ACCOUNT}). */
    AccountCapture fetchAccountInsights(SocialAccount account, String accessToken);

    /** Lista de publicaciones de la cuenta (scope {@code POSTS_LIST}). */
    PostsListCapture fetchPosts(SocialAccount account, String accessToken);

    /** Insights de UNA publicación (scope {@code POST}). */
    PostInsightsCapture fetchPostInsights(SocialAccount account, RawPost post, String accessToken);

    /** Stories activas (solo Instagram; vacío para el resto). */
    default List<StoryCapture> fetchStories(SocialAccount account, String accessToken) {
        return List.of();
    }

    /**
     * Métricas <b>extra</b> de cuenta del catálogo v1.1 que se piden en llamadas Graph aparte y
     * <b>best-effort</b> (splits {@code follow_type} de views/reach, {@code profile_views}, taps por
     * destino): {@code metric_key -> value}. <b>Nunca lanza</b> — si la API falla o no trae el campo,
     * devuelve lo que pudo (o vacío), para no tumbar la captura CORE de {@link #fetchAccountInsights}.
     * Default vacío: las redes/implementaciones que no lo soportan no escriben nada.
     */
    default AccountCapture fetchAccountExtras(SocialAccount account, String accessToken) {
        return new AccountCapture(null, null, java.util.Map.of());
    }

    /**
     * Métricas <b>extra</b> por publicación del catálogo v1.1 ({@code reposts}, {@code profile_visits},
     * {@code ig_reels_avg_watch_time}), pedidas en llamada aparte y <b>best-effort</b>. <b>Nunca lanza</b>.
     */
    default PostInsightsCapture fetchPostExtras(SocialAccount account, RawPost post, String accessToken) {
        return new PostInsightsCapture(null, null, java.util.Map.of());
    }

    /**
     * Demografía de audiencia (v1.1) → segmentos para {@code audience_demographics}. Llamada aparte y
     * <b>best-effort</b>; <b>nunca lanza</b>. Default vacío (solo IG la implementa en v1.1).
     */
    default AudienceDemographicsCapture fetchAudienceDemographics(SocialAccount account, String accessToken) {
        return new AudienceDemographicsCapture(null, null, List.of());
    }

    /** {@code true} si es el provider fake (selección preferente en local/test). */
    default boolean mock() {
        return false;
    }
}
