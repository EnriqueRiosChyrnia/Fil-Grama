package com.filgrama.metrics.dto;

import java.util.List;

/** Respuesta de {@code GET /api/v1/posts/{id}/metrics}: serie temporal de un post. */
public record PostSeriesResponse(
        Long postId,
        String metric,
        List<SeriesPoint> points) {
}
