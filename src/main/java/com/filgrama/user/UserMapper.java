package com.filgrama.user;

import org.springframework.stereotype.Component;

import com.filgrama.domain.User;
import com.filgrama.user.dto.UserResponse;

@Component
public class UserMapper {

    /** Mapea a la representación pública. Omite {@code passwordHash} por diseño. */
    public UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                u.isActive(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
