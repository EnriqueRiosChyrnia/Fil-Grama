package com.filgrama.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.filgrama.auth.AuthService;
import com.filgrama.auth.InvalidCredentialsException;
import com.filgrama.auth.token.RefreshTokenException;
import com.filgrama.auth.token.RefreshTokenService;
import com.filgrama.auth.web.dto.LoginRequest;
import com.filgrama.auth.web.dto.LoginResponse;
import com.filgrama.auth.web.dto.RefreshRequest;
import com.filgrama.auth.web.dto.RefreshResponse;
import com.filgrama.auth.web.dto.UserDto;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService authService;
    @Mock
    RefreshTokenService refreshTokenService;
    @InjectMocks
    AuthController controller;

    private MockHttpServletRequest http(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void loginSuccessReturns200WithBody() {
        when(authService.login("a@b.com", "pw"))
                .thenReturn(new LoginResponse("acc", "ref", new UserDto(1L, "a@b.com", "A", "ADMIN")));

        ResponseEntity<?> response =
                controller.login(new LoginRequest("a@b.com", "pw"), http("/api/v1/auth/login"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(LoginResponse.class);
    }

    @Test
    void loginInvalidReturns401ProblemDetail() {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        ResponseEntity<?> response =
                controller.login(new LoginRequest("a@b.com", "bad"), http("/api/v1/auth/login"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        assertThat(((ProblemDetail) response.getBody()).getStatus()).isEqualTo(401);
    }

    @Test
    void refreshSuccessReturns200() {
        when(refreshTokenService.rotate("r"))
                .thenReturn(new RefreshTokenService.RefreshResult("acc2", "ref2"));

        ResponseEntity<?> response =
                controller.refresh(new RefreshRequest("r"), http("/api/v1/auth/refresh"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(RefreshResponse.class);
    }

    @Test
    void refreshReuseReturns401() {
        when(refreshTokenService.rotate(any()))
                .thenThrow(new RefreshTokenException("reuse detected"));

        ResponseEntity<?> response =
                controller.refresh(new RefreshRequest("r"), http("/api/v1/auth/refresh"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
    }

    @Test
    void logoutReturns204AndRevokes() {
        ResponseEntity<Void> response = controller.logout(new RefreshRequest("r"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(refreshTokenService).revoke("r");
    }

    @Test
    void meReturnsCurrentUser() {
        when(authService.me(1L)).thenReturn(new UserDto(1L, "a@b.com", "A", "ADMIN"));

        UserDto dto = controller.me(new UsernamePasswordAuthenticationToken(1L, null));

        assertThat(dto.id()).isEqualTo(1L);
    }
}
