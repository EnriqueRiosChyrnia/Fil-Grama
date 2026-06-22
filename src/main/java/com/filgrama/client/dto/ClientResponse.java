package com.filgrama.client.dto;

import java.time.Instant;

import com.filgrama.domain.enums.ClientStatus;

/** Representación de un cliente devuelta por la API. */
public record ClientResponse(
        Long id,
        String name,
        String plan,
        String timezone,
        ClientStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
