package com.filgrama.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/**
 * El catch-all no debe degradar a 500 lo que la cadena de seguridad sabe formatear:
 * {@link AuthenticationException} debe re-lanzarse (→ 401 vía AuthenticationEntryPoint) y
 * {@link AccessDeniedException} idem (→ 403 vía AccessDeniedHandler). Una {@link ApiException}
 * sí se mapea a su status acá.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void reThrowsAuthenticationExceptionInsteadOfDegradingTo500() {
        AuthenticationException ex = new BadCredentialsException("bad token");
        assertThatThrownBy(() -> handler.handleAuthentication(ex)).isSameAs(ex);
    }

    @Test
    void reThrowsAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("denied");
        assertThatThrownBy(() -> handler.handleAccessDenied(ex)).isSameAs(ex);
    }

    @Test
    void mapsApiExceptionToItsStatus() {
        ProblemDetail pd = handler.handleApiException(ApiException.notFound("no existe"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
