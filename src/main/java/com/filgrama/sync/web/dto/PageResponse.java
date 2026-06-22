package com.filgrama.sync.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Sobre de paginación con el formato del contrato (spec/03):
 * {@code {content, page, size, totalElements, totalPages}}. Propio del track para no acoplar con
 * los controllers de otros paquetes (que ya definen su propia copia).
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
