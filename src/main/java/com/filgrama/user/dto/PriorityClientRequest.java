package com.filgrama.user.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/users/{id}/priority-clients}. */
public record PriorityClientRequest(
        @NotNull Long clientId) {
}
