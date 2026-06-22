package com.filgrama.oauth;

import java.time.Instant;

/**
 * Resultado de un refresh/re-exchange. {@code refreshToken} no nulo ⇒ la red rotó
 * el refresh token (TikTok) y hay que guardarlo cifrado.
 */
public record OAuthRefreshResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        String scopes,
        Instant expiresAt) {
}
