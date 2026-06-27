package com.filgrama.connectlink.dto;

/**
 * Body de creación de un connect-link. Ambos campos son opcionales:
 * <ul>
 *   <li>{@code platform} (ej. {@code "tiktok"}) — fija la red; null = el cliente elige.</li>
 *   <li>{@code accountId} — reconexión de una cuenta puntual (hereda el guard del open_id esperado).</li>
 * </ul>
 */
public record CreateConnectLinkRequest(String platform, Long accountId) {
}
