package com.filgrama.user.dto;

import com.filgrama.domain.enums.Role;

/** Cuerpo de {@code PATCH /api/v1/users/{id}} — campos opcionales. */
public record UpdateUserRequest(
        String fullName,
        Role role,
        Boolean isActive) {
}
