package com.filgrama.account.event;

/**
 * Se publica cuando una cuenta social queda recién conectada/reconectada con credencial válida
 * (callback OAuth exitoso). El track Sync lo escucha (AFTER_COMMIT) para disparar un
 * <b>escaneo inmediato SOLO de esa cuenta</b> (posts + métricas + miniaturas), best-effort:
 * si el escaneo falla, la cuenta queda conectada igual. TAREA A.
 *
 * @param accountId id de {@code social_accounts} ya persistido y commiteado.
 */
public record AccountConnectedEvent(Long accountId) {
}
