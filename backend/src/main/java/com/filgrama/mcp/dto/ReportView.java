package com.filgrama.mcp.dto;

import java.math.BigDecimal;
import java.util.List;

import com.filgrama.reports.data.ReportData;

/**
 * Salida de {@code get_client_report_data}: el {@link ReportData} que arma la app, proyectado para la
 * IA. Reusa los KPIs por red tal cual (métricas + deltas, engagement, alcance y los bloques v1.1:
 * demografía, split de visualizaciones, interacciones por acción, tipo de contenido, actividad de
 * perfil). Los posts destacados van SIN la miniatura base64 (ruido inútil para el modelo): solo lo
 * necesario para redactar. {@code narrativeMd} trae la narrativa ya guardada del período, si existe.
 */
public record ReportView(
        ReportData.Client client,
        ReportData.Period period,
        List<String> platforms,
        String rankBy,
        List<ReportData.PlatformKpis> byPlatform,
        List<PostView> topPosts,
        String narrativeMd) {

    /** Post destacado, liviano: sin miniatura embebida; con métrica de ranking y watch-time (reels). */
    public record PostView(
            Long id,
            String platform,
            String displayType,
            String publishedAtLocal,
            String permalink,
            String caption,
            String metricName,
            BigDecimal metricValue,
            BigDecimal watchTimeSeconds) {
    }
}
