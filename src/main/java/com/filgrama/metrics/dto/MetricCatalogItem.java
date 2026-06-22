package com.filgrama.metrics.dto;

/** Fila del catálogo de métricas expuesta por {@code GET /api/v1/metrics}. */
public record MetricCatalogItem(
        String key,
        String displayName,
        String platform,
        String level,
        String unit,
        String tier,
        String state) {
}
