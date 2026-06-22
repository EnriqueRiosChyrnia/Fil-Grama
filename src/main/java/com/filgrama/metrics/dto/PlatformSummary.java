package com.filgrama.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resumen de KPIs de una red para un cliente. Dirigido por el catálogo: solo incluye las
 * métricas CORE que esa red tiene cargadas (sin asumir paridad IG/FB/TikTok).
 * {@code engagementRate} y {@code followerGrowth} son derivados (null si la red no tiene los insumos).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlatformSummary(
        String platform,
        List<SummaryMetric> metrics,
        BigDecimal engagementRate,
        BigDecimal followerGrowth) {
}
