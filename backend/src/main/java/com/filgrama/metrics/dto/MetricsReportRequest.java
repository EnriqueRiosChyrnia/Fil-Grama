package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request de {@code POST /accounts/{id}/metrics:report} y {@code POST /posts/{id}/metrics:report}.
 * Patrón GA4 {@code runReport}: N métricas + rango en una sola llamada.
 */
public record MetricsReportRequest(
        @Schema(description = "1..N metric_key válidos del catálogo. Requerido. Métrica inexistente → 400.",
                example = "[\"ig_reach\", \"ig_followers_count\"]")
        List<String> metrics,

        @Schema(description = "Rango de fechas. Opcional; default = últimos 90 días (hoy-89 .. hoy). from>to → 400.")
        DateRange dateRange,

        @Schema(description = "Granularidad. Opcional, default 'day'. v1 solo soporta 'day'.",
                example = "day", defaultValue = "day")
        String granularity) {
}
