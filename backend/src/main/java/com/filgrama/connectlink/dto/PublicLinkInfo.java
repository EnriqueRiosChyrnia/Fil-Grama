package com.filgrama.connectlink.dto;

import java.time.Instant;
import java.util.List;

/**
 * Metadatos públicos de un connect-link para la página de conexión del cliente (sin login).
 * Acotado al cliente del token: no expone datos de otros clientes. {@code platform} null = el
 * cliente elige la red. {@code connectedAccounts} es la lista de cuentas ya conectadas del cliente
 * (checklist abierto del onboarding multi-cuenta) — <b>mínimo</b>: handle + red, sin métricas ni
 * tokens. Ver spec/09 §"Link compartible" / §"Onboarding multi-cuenta" y CU9.
 */
public record PublicLinkInfo(String clientName, String platform, Instant expiresAt,
                             List<ConnectedAccount> connectedAccounts) {

    /** Cuenta ya conectada del cliente. Mínimo a propósito: nunca métricas ni credenciales. */
    public record ConnectedAccount(String handle, String platform) {
    }
}
