package com.filgrama.oauth.state;

/** {@code state} con firma inválida, expirado o ya consumido (reuso anti-CSRF). */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
