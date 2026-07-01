package com.filgrama.reports.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportType;

/**
 * Contrato de datos compartido del reporte (spec/08). Lo arma el {@link ReportDataAssembler} con los
 * números que provee la app (servicios del track D + queries propias); <b>los renderers no calculan
 * cifras, sólo presentan</b>. Es el mismo JSON que en v2 consumirá la IA (MCP) para redactar el
 * "Análisis del mes" — por eso {@link #narrativeMd()} ya existe, nullable, y en v1 es siempre null.
 */
public record ReportData(
        ReportType reportType,
        ReportFormat format,
        Client client,
        Period period,
        List<String> platforms,
        String rankBy,
        List<PlatformKpis> kpis,
        List<ReportPost> topPosts,
        List<PostGroup> postGroups,
        List<PostGroup> storyGroups,
        Highlights postHighlights,
        Highlights storyHighlights,
        String narrativeMd) {

    /** El reporte muestra la sección "Análisis del mes" sólo si hay narrativa (en v1, nunca). */
    public boolean hasNarrative() {
        return narrativeMd != null && !narrativeMd.isBlank();
    }

    public record Client(Long id, String name, String timezone, String plan) {
    }

    public record Period(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {
    }

    /** KPI agregado de una métrica del período con su delta vs el período anterior (null si no hay). */
    public record Kpi(String key, String displayName, String unit, BigDecimal value, BigDecimal delta) {
    }

    /**
     * KPIs de una red: métricas CORE + derivados (engagement, crecimiento de seguidores) + alcance +
     * los bloques v1.1 del reporte mensual completo (research/06): demografía, split
     * seguidor/no-seguidor de visualizaciones, interacciones por acción, visualizaciones por tipo de
     * contenido y actividad de perfil. Todos nullable/vacíos si la red no trae el dato (degradación
     * elegante, spec/05 §v1.1) — nunca se inventa una cifra.
     */
    public record PlatformKpis(
            String platform,
            List<Kpi> metrics,
            BigDecimal engagementRate,
            BigDecimal followerGrowth,
            ReachEvolution reach,
            Demographics demographics,
            ViewsFollowerSplit viewsFollowerSplit,
            List<Kpi> interactionsByAction,
            List<ContentTypeShare> viewsByContentType,
            ProfileActivity profileActivity) {
    }

    /** Evolución del alcance: total del período vs el anterior y su variación porcentual. */
    public record ReachEvolution(BigDecimal current, BigDecimal previous, BigDecimal deltaPct) {
    }

    /**
     * Demografía de seguidores (research/06 §3, tabla {@code audience_demographics}). Cada lista viene
     * ordenada de mayor a menor {@code value}, con {@code pct} calculado sobre el total de ESA
     * dimensión (no necesariamente 100% entre los mostrados si el renderer trunca). Sólo IG/FB exponen
     * demografía por API estándar (TikTok queda fuera); nullable si la red/cuenta no la trae.
     */
    public record Demographics(
            List<Segment> cities, List<Segment> countries, List<Segment> ageRanges, List<Segment> genders) {

        public boolean isEmpty() {
            return isEmpty(cities) && isEmpty(countries) && isEmpty(ageRanges) && isEmpty(genders);
        }

        private static boolean isEmpty(List<Segment> list) {
            return list == null || list.isEmpty();
        }
    }

    /** Un segmento de una dimensión de demografía (ej. {@code "Encarnación"}, 214, 0.27). */
    public record Segment(String label, BigDecimal value, BigDecimal pct) {
    }

    /**
     * Split seguidor/no-seguidor de VISUALIZACIONES (research/06 §2: el split de INTERACCIONES no
     * existe en el API, sólo el de views). Nullable si la red no captura {@code *_views_followers}/
     * {@code *_views_non_followers}.
     */
    public record ViewsFollowerSplit(
            BigDecimal followers, BigDecimal nonFollowers, BigDecimal followerPct, BigDecimal nonFollowerPct) {
    }

    /** Visualizaciones agregadas por tipo de contenido (Reels/Feed/Stories…), derivado (spec/05 §v1.1). */
    public record ContentTypeShare(String displayType, BigDecimal views, BigDecimal pct) {
    }

    /** Actividad en el perfil: visitas y taps a los botones de contacto. Nullable si la red no lo trae. */
    public record ProfileActivity(BigDecimal profileViews, BigDecimal whatsappTaps, BigDecimal directionTaps) {
    }

    /**
     * Post o historia listo para renderizar. {@code displayType} es la etiqueta de sección
     * (Reels/Feed/Stories…). La miniatura va embebida como data-URI base64 ({@code thumbnailDataUri})
     * si hay cache; si no, queda {@code thumbnailUrl} (remoto, diferido). {@code metricValue} es el
     * valor de la métrica de ranking ({@code rankBy}) para este post. {@code watchTimeSeconds} es el
     * tiempo medio de visionado (sólo reels; research/06 §1, {@code ig_reels_avg_watch_time}), null si
     * no aplica o no se capturó.
     */
    public record ReportPost(
            Long id,
            String platform,
            String postType,
            String displayType,
            Instant publishedAt,
            String publishedAtLocal,
            String permalink,
            String caption,
            String thumbnailDataUri,
            String thumbnailUrl,
            boolean story,
            String metricKey,
            String metricName,
            BigDecimal metricValue,
            BigDecimal watchTimeSeconds) {

        /** Copia con la miniatura resuelta (enriquecida tras decidir qué posts se renderizan). */
        public ReportPost withThumbnail(String dataUri, String url) {
            return new ReportPost(id, platform, postType, displayType, publishedAt, publishedAtLocal,
                    permalink, caption, dataUri, url, story, metricKey, metricName, metricValue, watchTimeSeconds);
        }
    }

    /** Una sección "como con como": misma red y mismo tipo, cronológica (más nuevo primero). */
    public record PostGroup(String platform, String displayType, List<ReportPost> posts) {
    }

    /** Destacadas (top, por ranking) y con más margen de mejora (bottom, en tono constructivo). */
    public record Highlights(List<ReportPost> top, List<ReportPost> bottom) {

        public boolean isEmpty() {
            return (top == null || top.isEmpty()) && (bottom == null || bottom.isEmpty());
        }
    }
}
