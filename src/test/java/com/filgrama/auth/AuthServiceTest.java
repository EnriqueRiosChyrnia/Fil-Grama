package com.filgrama.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.filgrama.auth.security.AuthUserDetails;
import com.filgrama.auth.security.DbUserDetailsService;
import com.filgrama.auth.token.RefreshTokenService;
import com.filgrama.auth.web.dto.LoginResponse;
import com.filgrama.domain.User;
import com.filgrama.domain.enums.Role;
import com.filgrama.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    DbUserDetailsService userDetailsService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    UserRepository userRepository;

    JwtService jwtService;
    AuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-test-secret-test-secret-test-secret-0123456789-abc");
        jwtService = new JwtService(props);
        service = new AuthService(userDetailsService, passwordEncoder, jwtService,
                refreshTokenService, userRepository);
    }

    private User user(boolean active) {
        User u = new User();
        u.setId(1L);
        u.setEmail("admin@filgrama.local");
        u.setPasswordHash("$2a$hash");
        u.setFullName("Admin");
        u.setRole(Role.ADMIN);
        u.setActive(active);
        return u;
    }

    @Test
    void loginValidReturnsTokensAndUser() {
        when(userDetailsService.loadUserByUsername(eq("admin@filgrama.local")))
                .thenReturn(new AuthUserDetails(user(true)));
        when(passwordEncoder.matches("secret", "$2a$hash")).thenReturn(true);
        when(refreshTokenService.issueNew(1L)).thenReturn("refresh-raw");

        LoginResponse resp = service.login("admin@filgrama.local", "secret");

        assertThat(resp.refreshToken()).isEqualTo("refresh-raw");
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.user().id()).isEqualTo(1L);
        assertThat(resp.user().role()).isEqualTo("ADMIN");
        // el access token es parseable y trae el sub correcto
        assertThat(jwtService.parse(resp.accessToken()).userId()).isEqualTo(1L);
    }

    @Test
    void loginWrongPasswordThrows() {
        when(userDetailsService.loadUserByUsername(any()))
                .thenReturn(new AuthUserDetails(user(true)));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.login("admin@filgrama.local", "bad"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginInactiveUserThrows() {
        when(userDetailsService.loadUserByUsername(any()))
                .thenReturn(new AuthUserDetails(user(false)));
        // matches no debería ni consultarse: el usuario inactivo se rechaza antes
        lenient().when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.login("admin@filgrama.local", "secret"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginUnknownUserThrows() {
        when(userDetailsService.loadUserByUsername(any()))
                .thenThrow(new UsernameNotFoundException("nope"));

        assertThatThrownBy(() -> service.login("ghost@filgrama.local", "secret"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void meReturnsCurrentUser() {
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user(true)));

        assertThat(service.me(1L).email()).isEqualTo("admin@filgrama.local");
    }

    @Test
    void meUnknownUserThrows() {
        when(userRepository.findById(anyLong())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.me(99L))
                .isInstanceOf(com.filgrama.error.ApiException.class);
    }
}
