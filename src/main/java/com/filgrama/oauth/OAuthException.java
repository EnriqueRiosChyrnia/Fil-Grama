package com.filgrama.oauth;

/** Falla del canje/refresh OAuth (red caída, code inválido, respuesta inesperada). */
public class OAuthException extends RuntimeException {

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
