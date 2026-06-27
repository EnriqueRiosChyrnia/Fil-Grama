package com.filgrama.account.dto;

/**
 * Resultado del <b>reconectar inteligente</b> ({@code POST /accounts/{id}/reconnect}):
 * <ul>
 *   <li>token vivo → {@code status=CONNECTED}, {@code requiresReauth=false} (sin OAuth ni cliente);</li>
 *   <li>token muerto / sin credencial → {@code status=ERROR}, {@code requiresReauth=true} (el front
 *       ofrece re-autorizar la agencia con {@code connect ?accountId=} o generar un connect-link).</li>
 * </ul>
 * Ver spec/09 §"Ciclo de vida" y CU10.
 */
public record ReconnectResponse(String status, boolean requiresReauth) {
}
