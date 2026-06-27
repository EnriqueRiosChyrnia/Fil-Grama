package com.filgrama.connectlink.dto;

/**
 * Respuesta del arranque público de OAuth vía connect-link: el front redirige a
 * {@code authorizationUrl} (pantalla oficial de la red). Ver spec/09 §"Link compartible".
 */
public record AuthorizationUrlResponse(String authorizationUrl) {
}
