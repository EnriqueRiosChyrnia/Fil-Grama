package com.filgrama.account.dto;

/**
 * Respuesta de {@code connect}: el front abre {@code authorizationUrl} y conserva el
 * {@code state} opaco (de un solo uso). El front nunca recibe tokens.
 */
public record ConnectResponse(String authorizationUrl, String state) {
}
