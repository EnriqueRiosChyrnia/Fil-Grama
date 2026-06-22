package com.filgrama.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Body de {@code /auth/refresh} y {@code /auth/logout}. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
