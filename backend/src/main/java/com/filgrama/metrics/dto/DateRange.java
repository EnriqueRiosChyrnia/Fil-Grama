package com.filgrama.metrics.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Rango de fechas inclusivo de un informe. Ambos límites opcionales en la request; el servicio
 * los resuelve (default = últimos 90 días) y la response siempre los devuelve concretos.
 */
public record DateRange(
        @Schema(description = "Primer día incluido (inclusive).", example = "2026-03-24")
        LocalDate from,

        @Schema(description = "Último día incluido (inclusive).", example = "2026-06-22")
        LocalDate to) {
}
