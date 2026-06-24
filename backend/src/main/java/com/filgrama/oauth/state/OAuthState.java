package com.filgrama.oauth.state;

import com.filgrama.domain.enums.Platform;

/**
 * Contenido validado de un {@code state} OAuth. Mapea el callback a su origen:
 * {@code clientId + platform + usuario iniciador}. El {@code nonce} (jti) lo hace
 * de un solo uso.
 *
 * <p>{@code expectedExternalAccountId} (TAREA B): cuando el flujo es una <b>reconexión</b> de una
 * cuenta ya conocida, lleva el {@code external_account_id} (open_id) esperado. El callback exige
 * que el {@code open_id} devuelto por la red coincida; si no, rechaza sin linkear (evita enganchar
 * la cuenta equivocada por la sesión activa del navegador). {@code null} en un connect nuevo.
 */
public record OAuthState(Long clientId, Platform platform, Long userId, String nonce,
                         String expectedExternalAccountId) {
}
