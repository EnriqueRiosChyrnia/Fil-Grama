package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response de {@code POST /posts/{id}/metrics:report}: mismo shape que la de cuenta, con {@code postId}. */
public record PostReportResponse(
        @Schema(description = "Post del informe.", example = "5")
        Long postId,

        @Schema(description = "Rango efectivo del informe (ya resuelto a fechas concretas).")
        DateRange dateRange,

        @Schema(description = "Granularidad efectiva.", example = "day")
        String granularity,

        @Schema(description = "Una serie por métrica pedida, en el mismo orden.")
        List<MetricSeries> series) {
}
