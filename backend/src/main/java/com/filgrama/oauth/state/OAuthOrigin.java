package com.filgrama.oauth.state;

/**
 * De dónde nació el flujo OAuth, para que el callback elija el destino correcto (spec/09 §Link
 * compartible). {@code APP}: la agencia inició el connect desde la app (tiene sesión). {@code LINK}:
 * el cliente entró por un link compartible (sin sesión en Fil-Grama) → el callback redirige a una
 * página pública de éxito/error.
 */
public enum OAuthOrigin {
    APP,
    LINK
}
