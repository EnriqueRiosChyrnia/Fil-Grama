package com.filgrama.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 para requests sin autenticación válida a endpoints protegidos.
 * Emite {@code application/problem+json} con el formato del handler global.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ProblemSupport.writeProblemJson(response, HttpStatus.UNAUTHORIZED,
                "Authentication required", request.getRequestURI());
    }
}
