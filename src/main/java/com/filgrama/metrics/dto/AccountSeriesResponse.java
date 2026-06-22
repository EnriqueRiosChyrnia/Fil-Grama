package com.filgrama.metrics.dto;

import java.util.List;

/** Respuesta de {@code GET /api/v1/accounts/{id}/metrics}: serie temporal de una cuenta. */
public record AccountSeriesResponse(
        Long accountId,
        String metric,
        String granularity,
        List<SeriesPoint> points) {
}
