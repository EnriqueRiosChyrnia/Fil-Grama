package com.filgrama.metrics.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Respuesta de {@code GET /api/v1/clients/{clientId}/summary}: KPIs agregados por red. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryResponse(
        Long clientId,
        LocalDate from,
        LocalDate to,
        List<PlatformSummary> platforms) {
}
