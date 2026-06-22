package com.filgrama.client.dto;

import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;

/**
 * Resumen liviano de una cuenta conectada (track C). Lectura desde
 * {@code SocialAccountRepository}; no se crean entidades de cuentas acá.
 */
public record AccountSummary(
        Platform platform,
        AccountStatus status,
        String handle) {
}
