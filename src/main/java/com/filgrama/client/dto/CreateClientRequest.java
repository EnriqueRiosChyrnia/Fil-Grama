package com.filgrama.client.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/v1/clients}. */
public record CreateClientRequest(
        @NotBlank String name,
        String notes,
        String plan,
        String timezone) {
}
