package com.filgrama.auth.token;

/**
 * Refresh token inválido, expirado, desconocido o reusado. La traduce el
 * {@code AuthController} a {@code 401 application/problem+json} (NO pasa por el
 * advice global; los 401 los formatea el track Auth).
 */
public class RefreshTokenException extends RuntimeException {
    public RefreshTokenException(String message) {
        super(message);
    }
}
