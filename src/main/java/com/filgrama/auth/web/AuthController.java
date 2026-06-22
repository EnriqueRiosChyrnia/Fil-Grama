package com.filgrama.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.auth.AuthService;
import com.filgrama.auth.InvalidCredentialsException;
import com.filgrama.auth.security.ProblemSupport;
import com.filgrama.auth.token.RefreshTokenException;
import com.filgrama.auth.token.RefreshTokenService;
import com.filgrama.auth.web.dto.LoginRequest;
import com.filgrama.auth.web.dto.RefreshRequest;
import com.filgrama.auth.web.dto.RefreshResponse;
import com.filgrama.auth.web.dto.UserDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Endpoints de autenticación (base {@code /api/v1/auth}). Contratos en spec/03.
 *
 * <p>Los 401 (login/refresh inválido) se devuelven directamente como
 * {@code application/problem+json}, sin pasar por el advice global (que no maneja 401).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        try {
            return ResponseEntity.ok(authService.login(request.email(), request.password()));
        } catch (InvalidCredentialsException e) {
            return unauthorized(http, e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        try {
            RefreshTokenService.RefreshResult result = refreshTokenService.rotate(request.refreshToken());
            return ResponseEntity.ok(new RefreshResponse(result.accessToken(), result.refreshToken()));
        } catch (RefreshTokenException e) {
            return unauthorized(http, "Invalid refresh token");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        long userId = (Long) authentication.getPrincipal();
        return authService.me(userId);
    }

    private ResponseEntity<ProblemDetail> unauthorized(HttpServletRequest http, String detail) {
        ProblemDetail pd = ProblemSupport.problem(
                HttpStatus.UNAUTHORIZED, "Unauthorized", detail, http.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
