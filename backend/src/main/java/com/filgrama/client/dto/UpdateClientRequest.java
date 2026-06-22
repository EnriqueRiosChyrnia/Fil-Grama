package com.filgrama.client.dto;

/** Cuerpo de {@code PATCH /api/v1/clients/{id}} — todos los campos opcionales. */
public record UpdateClientRequest(
        String name,
        String notes,
        String plan,
        String timezone) {
}
