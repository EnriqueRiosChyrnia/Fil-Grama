package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response de {@code POST /accounts/{id}/metrics:report}: una serie por métrica, lista para graficar. */
public record AccountReportResponse(
        @Schema(description = "Cuenta del informe.", example = "7")
        Long accountId,

        @Schema(description = "Rango efectivo del informe (ya resuelto a fechas concretas).")
        DateRange dateRange,

        @Schema(description = "Granularidad efectiva.", example = "day")
        String granularity,

        @Schema(description = "Una serie por métrica pedida, en el mismo orden.")
        List<MetricSeries> series) {
}
