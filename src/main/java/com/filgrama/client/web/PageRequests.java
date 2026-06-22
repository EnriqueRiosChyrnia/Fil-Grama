package com.filgrama.client.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Construye un {@link Pageable} desde los params del contrato
 * {@code ?page=&size=&sort=campo,desc}, sin depender del resolver de Spring Data
 * Web (para que los slice tests sean deterministas).
 */
public final class PageRequests {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(safePage, safeSize);
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (field.isEmpty()) {
            return PageRequest.of(safePage, safeSize);
        }
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, field));
    }
}
