package com.filgrama.metrics.dto;

import java.util.List;

/** Sobre de paginación del contrato (spec/03): {@code {content, page, size, totalElements, totalPages}}. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
