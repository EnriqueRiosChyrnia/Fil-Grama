package com.filgrama.client.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Envoltorio de paginación con el formato EXACTO del contrato (spec/03):
 * {@code {content, page, size, totalElements, totalPages}}.
 *
 * <p>Compartido por los controllers de los paquetes {@code com.filgrama.client}
 * y {@code com.filgrama.user} (ambos del mismo track).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
