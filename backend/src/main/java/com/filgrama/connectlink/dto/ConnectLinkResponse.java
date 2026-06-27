package com.filgrama.connectlink.dto;

import java.time.Instant;

/**
 * Respuesta de creación de un connect-link. El {@code token} raw se devuelve <b>solo aquí</b>
 * (nunca se persiste ni se loguea): en DB vive sólo su {@code sha-256}. El front arma/comparte
 * {@code url} (página pública {@code /connect/{token}}). Ver spec/09 §"Link compartible".
 */
public record ConnectLinkResponse(String token, String url, Instant expiresAt) {
}
