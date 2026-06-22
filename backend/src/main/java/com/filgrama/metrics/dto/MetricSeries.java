package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una serie por métrica dentro de un informe: la {@code unit} sale del catálogo. */
public record MetricSeries(
        @Schema(description = "metric_key del catálogo.", example = "ig_reach")
        String metric,

        @Schema(description = "Unidad de la métrica, tomada del catálogo (Metric.unit).", example = "count")
        String unit,

        @Schema(description = "Puntos de la serie, ordenados por día. Rango sin datos → lista vacía.")
        List<SeriesPoint> points) {
}
