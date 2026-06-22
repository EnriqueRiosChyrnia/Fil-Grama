package com.filgrama.auth.web.dto;

import com.filgrama.domain.User;

/** Vista pública de un usuario. Nunca incluye {@code password_hash}. */
public record UserDto(Long id, String email, String fullName, String role) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
