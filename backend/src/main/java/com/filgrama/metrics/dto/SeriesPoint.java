package com.filgrama.metrics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Punto de una serie temporal lista para graficar: día de captura + valor.
 * Granularidad v1 = {@code day} → {@code date} es el {@code capture_date} del snapshot.
 */
public record SeriesPoint(
        @Schema(description = "Día de la observación (capture_date). v1: granularidad 'day'.",
                example = "2026-06-01")
        LocalDate date,

        @Schema(description = "Valor de la métrica en ese día.", example = "12450")
        BigDecimal value) {
}
