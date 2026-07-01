package com.filgrama.sync.capture;

import java.time.LocalDate;
import java.util.List;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.AccountReachSeriesCapture;
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

    /**
     * Insights de cuenta (scope {@code ACCOUNT}) que Meta entrega como {@code total_value} de un
     * rango (spec/10 FG-CS-CAP): {@code windowSince}..{@code windowUntil} (~30 días) para que la
     * cuenta muestre números aunque el día de captura no tuvo actividad. {@code reach} NO va acá
     * (es {@code time_series}; ver {@link #fetchAccountReachSeries}).
     */
    AccountCapture fetchAccountInsights(SocialAccount account, String accessToken,
            LocalDate windowSince, LocalDate windowUntil);

    /**
     * Serie histórica de {@code reach} (única métrica de cuenta con {@code time_series} en Meta,
     * spec/05): un valor por día en {@code [since, until]}. <b>CORE, no best-effort</b> — un fallo
     * se retiene igual que {@link #fetchAccountInsights} (el {@code Retrier} del job la reintenta).
     * Default vacío: solo Instagram la implementa (Facebook/TikTok no tienen {@code reach} en el
     * catálogo v1).
     */
    default AccountReachSeriesCapture fetchAccountReachSeries(SocialAccount account, String accessToken,
            LocalDate since, LocalDate until) {
        return new AccountReachSeriesCapture(null, null, List.of());
    }

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
     * <b>best-effort</b> ({@code follows_and_unfollows}, splits {@code follow_type} de views/reach,
     * {@code profile_views}, taps por destino): {@code metric_key -> value}. Mismo rango
     * {@code windowSince}..{@code windowUntil} (~30 días) que {@link #fetchAccountInsights} — son
     * {@code total_value} de período, igual de sensibles a la ventana de 1 día. <b>Nunca lanza</b> —
     * si la API falla o no trae el campo, devuelve lo que pudo (o vacío), para no tumbar la captura
     * CORE. Default vacío: las redes/implementaciones que no lo soportan no escriben nada.
     */
    default AccountCapture fetchAccountExtras(SocialAccount account, String accessToken,
            LocalDate windowSince, LocalDate windowUntil) {
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
