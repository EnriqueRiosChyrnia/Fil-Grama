package com.filgrama.auth;

/**
 * Credenciales inválidas o usuario inactivo en {@code /auth/login}. La traduce el
 * {@code AuthController} a {@code 401 application/problem+json} (los 401 no pasan
 * por el advice global).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
