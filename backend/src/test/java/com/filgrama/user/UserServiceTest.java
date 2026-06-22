package com.filgrama.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.filgrama.domain.User;
import com.filgrama.domain.enums.Role;
import com.filgrama.error.ApiException;
import com.filgrama.repository.UserRepository;
import com.filgrama.user.dto.CreateUserRequest;
import com.filgrama.user.dto.UpdateUserRequest;
import com.filgrama.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    @Mock UserQueryRepository userQuery;
    @Mock PasswordEncoder encoder;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, userQuery, encoder, new UserMapper());
    }

    @Test
    void createHashesPasswordAndNeverExposesHash() {
        when(users.existsByEmail("a@b.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("HASHED");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse r = service.create(new CreateUserRequest("a@b.com", "Ana", Role.EMPLEADO, "password123"));

        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.email()).isEqualTo("a@b.com");
        assertThat(r.role()).isEqualTo(Role.EMPLEADO);
        assertThat(r.isActive()).isTrue();

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(users).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("HASHED");
        // Garantía estructural: UserResponse no tiene componente passwordHash.
    }

    @Test
    void createDuplicateEmailThrowsConflict() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("a@b.com", "Ana", Role.ADMIN, "password123")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void getNotFoundThrows404() {
        when(users.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateChangesProvidedFields() {
        User u = new User();
        u.setEmail("a@b.com");
        u.setFullName("Ana");
        u.setRole(Role.EMPLEADO);
        u.setActive(true);
        when(users.findById(1L)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse r = service.update(1L, new UpdateUserRequest("Ana Maria", Role.ADMIN, false));

        assertThat(r.fullName()).isEqualTo("Ana Maria");
        assertThat(r.role()).isEqualTo(Role.ADMIN);
        assertThat(r.isActive()).isFalse();
    }
}
