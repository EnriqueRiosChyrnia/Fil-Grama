package com.filgrama.user.dto;

import com.filgrama.domain.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/users}. */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotNull Role role,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password) {
}
