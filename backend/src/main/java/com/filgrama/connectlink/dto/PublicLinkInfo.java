package com.filgrama.connectlink.dto;

import java.time.Instant;

/**
 * Metadatos públicos de un connect-link para la página de conexión del cliente (sin login).
 * Acotado al cliente del token: no expone datos de otros clientes. {@code platform} null = el
 * cliente elige la red. Ver spec/09 §"Link compartible" y CU9.
 */
public record PublicLinkInfo(String clientName, String platform, Instant expiresAt) {
}
