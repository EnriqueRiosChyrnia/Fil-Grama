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

    /** KPIs de una red: métricas CORE + derivados (engagement, crecimiento de seguidores) + alcance. */
    public record PlatformKpis(
            String platform,
            List<Kpi> metrics,
            BigDecimal engagementRate,
            BigDecimal followerGrowth,
            ReachEvolution reach) {
    }

    /** Evolución del alcance: total del período vs el anterior y su variación porcentual. */
    public record ReachEvolution(BigDecimal current, BigDecimal previous, BigDecimal deltaPct) {
    }

    /**
     * Post o historia listo para renderizar. {@code displayType} es la etiqueta de sección
     * (Reels/Feed/Stories…). La miniatura va embebida como data-URI base64 ({@code thumbnailDataUri})
     * si hay cache; si no, queda {@code thumbnailUrl} (remoto, diferido). {@code metricValue} es el
     * valor de la métrica de ranking ({@code rankBy}) para este post.
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
            BigDecimal metricValue) {

        /** Copia con la miniatura resuelta (enriquecida tras decidir qué posts se renderizan). */
        public ReportPost withThumbnail(String dataUri, String url) {
            return new ReportPost(id, platform, postType, displayType, publishedAt, publishedAtLocal,
                    permalink, caption, dataUri, url, story, metricKey, metricName, metricValue);
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
