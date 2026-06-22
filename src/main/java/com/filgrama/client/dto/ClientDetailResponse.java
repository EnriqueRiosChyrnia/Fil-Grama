package com.filgrama.client.dto;

import java.time.Instant;
import java.util.List;

import com.filgrama.domain.enums.ClientStatus;

/** Detalle de un cliente + resumen de cuentas conectadas ({@code GET /clients/{id}}). */
public record ClientDetailResponse(
        Long id,
        String name,
        String plan,
        String timezone,
        ClientStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<AccountSummary> accountsSummary) {
}
