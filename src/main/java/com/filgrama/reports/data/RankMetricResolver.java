package com.filgrama.reports.data;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.filgrama.domain.Metric;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.service.MetricCatalogService;

/**
 * Traduce el {@code rankBy} (métrica lógica del request, ej. {@code reach}/{@code engagement}) al
 * {@code metric_key} de nivel POST concreto de cada red, ya que las métricas son por red y no hay
 * paridad IG/FB/TikTok (igual que el alias {@code engagement} del track D). Las historias usan su
 * propia métrica de alcance. Si {@code rankBy} ya es un {@code metric_key} del catálogo, se respeta;
 * si no existe y no es un alias conocido → 422.
 */
@Component
public class RankMetricResolver {

    /** Alias lógico → metric_key de post por red. Falta de insumo en una red = null (se omite). */
    private static final Map<String, Map<String, String>> ALIASES = Map.of(
            "reach", Map.of(
                    "INSTAGRAM", "ig_post_reach",
                    "FACEBOOK", "fb_post_views",
                    "TIKTOK", "tt_view_count"),
            "views", Map.of(
                    "INSTAGRAM", "ig_post_views",
                    "FACEBOOK", "fb_post_video_views",
                    "TIKTOK", "tt_view_count"),
            "engagement", Map.of(
                    "INSTAGRAM", "ig_post_total_interactions",
                    "FACEBOOK", "fb_post_engaged_users",
                    "TIKTOK", "tt_like_count"),
            "likes", Map.of(
                    "INSTAGRAM", "ig_post_likes",
                    "FACEBOOK", "fb_post_reactions_total",
                    "TIKTOK", "tt_like_count"));

    /** Alcance de historias (sólo IG hoy). */
    private static final String STORY_REACH_KEY = "ig_story_reach";

    public static final String DEFAULT_RANK_BY = "reach";

    private final MetricCatalogService catalog;

    public RankMetricResolver(MetricCatalogService catalog) {
        this.catalog = catalog;
    }

    /** Métrica de ranking resuelta para un post. */
    public record ResolvedMetric(String key, String displayName) {
    }

    /** Normaliza el {@code rankBy} para persistir/echar en el reporte (default si viene vacío). */
    public String normalize(String rankBy) {
        return rankBy == null || rankBy.isBlank() ? DEFAULT_RANK_BY : rankBy.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Valida que el {@code rankBy} sea un alias conocido o un {@code metric_key} existente de nivel
     * POST. Llamar una vez por reporte. Alias desconocido / metric inexistente → 422.
     */
    public void validate(String rankBy) {
        String key = normalize(rankBy);
        if (ALIASES.containsKey(key)) {
            return;
        }
        // No es alias: tiene que ser un metric_key real del catálogo.
        catalog.find(key)
                .orElseThrow(() -> ApiException.unprocessable(
                        "rankBy '%s' no es una métrica conocida (use reach|views|engagement|likes o un metric_key)"
                                .formatted(rankBy)));
    }

    /**
     * Resuelve la métrica de ranking para un post según su red, si es historia y el {@code rankBy}.
     * Devuelve {@code null} si la red no tiene esa métrica (se rankea sin valor → queda al final).
     */
    public ResolvedMetric resolve(String platform, boolean story, String rankBy) {
        String key = normalize(rankBy);
        String resolvedKey;
        if (ALIASES.containsKey(key)) {
            if (story && "reach".equals(key) && "INSTAGRAM".equals(platform)) {
                resolvedKey = STORY_REACH_KEY;
            } else {
                resolvedKey = ALIASES.get(key).get(platform);
            }
        } else {
            // metric_key explícito: úsalo sólo si pertenece a la red del post (o aplica a todas).
            Optional<Metric> metric = catalog.find(key);
            resolvedKey = metric
                    .filter(m -> m.getPlatform() == null || m.getPlatform().equalsIgnoreCase(platform))
                    .map(Metric::getKey)
                    .orElseGet(() -> ALIASES.get(DEFAULT_RANK_BY).get(platform));
        }
        if (resolvedKey == null) {
            return null;
        }
        String displayName = catalog.find(resolvedKey).map(Metric::getDisplayName).orElse(resolvedKey);
        return new ResolvedMetric(resolvedKey, displayName);
    }
}
