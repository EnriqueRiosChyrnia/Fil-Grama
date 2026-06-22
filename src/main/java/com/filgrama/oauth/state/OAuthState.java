package com.filgrama.oauth.state;

import com.filgrama.domain.enums.Platform;

/**
 * Contenido validado de un {@code state} OAuth. Mapea el callback a su origen:
 * {@code clientId + platform + usuario iniciador}. El {@code nonce} (jti) lo hace
 * de un solo uso.
 */
public record OAuthState(Long clientId, Platform platform, Long userId, String nonce) {
}
