package com.filgrama.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 403 para usuarios autenticados sin permiso (ej. {@code EMPLEADO} en endpoint
 * {@code @PreAuthorize("hasRole('ADMIN')")}). Mismo formato problem+json.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ProblemSupport.writeProblemJson(response, HttpStatus.FORBIDDEN,
                "Access denied", request.getRequestURI());
    }
}
