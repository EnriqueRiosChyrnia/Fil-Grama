package com.filgrama.user.dto;

import java.time.Instant;

import com.filgrama.domain.enums.Role;

/** Usuario expuesto por la API. NUNCA incluye {@code passwordHash}. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {
}
