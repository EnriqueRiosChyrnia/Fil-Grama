package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un informe de cuenta dentro de un {@code :batchReport} (espeja una request de {@code :report}). */
public record AccountReportRequest(
        @Schema(description = "Cuenta del informe. Requerido. Cuenta inexistente → falla todo el batch (404).",
                example = "7")
        Long accountId,

        @Schema(description = "1..N metric_key válidos del catálogo. Requerido.",
                example = "[\"ig_reach\"]")
        List<String> metrics,

        @Schema(description = "Rango de fechas. Opcional; default = últimos 90 días.")
        DateRange dateRange,

        @Schema(description = "Granularidad. Opcional, default 'day'.", example = "day", defaultValue = "day")
        String granularity) {
}
