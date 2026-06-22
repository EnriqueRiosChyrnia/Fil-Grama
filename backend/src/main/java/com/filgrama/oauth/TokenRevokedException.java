package com.filgrama.oauth;

/**
 * La autorización fue revocada por el cliente (o cambió la contraseña): la red
 * responde error de auth. El servicio marca la cuenta {@code ERROR} para forzar
 * re-onboarding.
 */
public class TokenRevokedException extends OAuthException {

    public TokenRevokedException(String message) {
        super(message);
    }
}
