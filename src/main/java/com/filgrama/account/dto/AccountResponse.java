package com.filgrama.account.dto;

import java.time.Instant;

import com.filgrama.domain.SocialAccount;

/**
 * Vista pública de una cuenta social. <b>Nunca</b> incluye tokens ni nada de
 * {@code account_credentials}: las credenciales viven solo cifradas server-side.
 */
public record AccountResponse(
        Long id,
        Long clientId,
        String platform,
        String externalAccountId,
        String handle,
        String displayName,
        String accountType,
        String status,
        String capabilities,
        Instant capabilitiesCheckedAt,
        Long connectedBy,
        Instant connectedAt) {

    public static AccountResponse from(SocialAccount a) {
        return new AccountResponse(
                a.getId(),
                a.getClientId(),
                a.getPlatform() != null ? a.getPlatform().name() : null,
                a.getExternalAccountId(),
                a.getHandle(),
                a.getDisplayName(),
                a.getAccountType() != null ? a.getAccountType().name() : null,
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getCapabilities(),
                a.getCapabilitiesCheckedAt(),
                a.getConnectedBy(),
                a.getConnectedAt());
    }
}
