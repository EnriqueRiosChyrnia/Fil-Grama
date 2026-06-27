package com.filgrama.connectlink.dto;

import java.time.Instant;

import com.filgrama.connectlink.ConnectLink;

/**
 * Vista de un connect-link para la agencia (listado). <b>Nunca</b> incluye el token raw ni el hash:
 * el raw sólo existe en la respuesta de creación. Ver spec/03 §connect-links.
 */
public record ConnectLinkSummary(
        Long id,
        Long clientId,
        String platform,
        Long expectedAccountId,
        Long createdBy,
        Instant expiresAt,
        Instant revokedAt,
        Instant usedAt,
        Instant createdAt) {

    public static ConnectLinkSummary from(ConnectLink link) {
        return new ConnectLinkSummary(
                link.getId(),
                link.getClientId(),
                link.getPlatform() != null ? link.getPlatform().name() : null,
                link.getExpectedAccountId(),
                link.getCreatedBy(),
                link.getExpiresAt(),
                link.getRevokedAt(),
                link.getUsedAt(),
                link.getCreatedAt());
    }
}
