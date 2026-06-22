package com.filgrama.metrics.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ítem de la página de posts de {@code GET /api/v1/accounts/{id}/posts}.
 * {@code sortValue} sólo aparece cuando se ordena por una métrica (valor usado para el ranking).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostListItem(
        Long id,
        Long accountId,
        String platform,
        String externalPostId,
        String postType,
        String permalink,
        String caption,
        String remoteThumbnailUrl,
        Instant publishedAt,
        BigDecimal sortValue) {
}
