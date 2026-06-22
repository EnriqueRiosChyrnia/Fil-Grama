package com.filgrama.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.filgrama.error.ApiException;

/**
 * Resuelve el id del usuario autenticado desde el {@link SecurityContextHolder}.
 *
 * <p>El track A (Auth) pone el {@code userId} (claim {@code sub} del JWT) como
 * nombre del principal. Mientras A no esté mergeado en esta rama no hay auth real;
 * para poder probar {@code /me/...} en dev se permite un FALLBACK documentado vía
 * la propiedad {@code filgrama.dev.fallback-user-id} (ausente por defecto). El
 * código SIEMPRE lee primero del contexto de seguridad, nunca de un header arbitrario.
 */
@Component
public class CurrentUserProvider {

    private final Long devFallbackUserId;

    public CurrentUserProvider(
            @Value("${filgrama.dev.fallback-user-id:#{null}}") Long devFallbackUserId) {
        this.devFallbackUserId = devFallbackUserId;
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)) {
            try {
                return Long.valueOf(auth.getName());
            } catch (NumberFormatException ignored) {
                // principal no numérico: cae al fallback / 401
            }
        }
        if (devFallbackUserId != null) {
            return devFallbackUserId;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "No authenticated user in security context");
    }
}
