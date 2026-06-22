package com.filgrama.sync.web.dto;

import com.filgrama.domain.SyncAccountResult;

/** Resultado por cuenta dentro de una corrida. */
public record SyncAccountResultResponse(
        Long id,
        Long accountId,
        String status,
        Integer metricsCaptured,
        String errorMessage) {

    public static SyncAccountResultResponse from(SyncAccountResult result) {
        return new SyncAccountResultResponse(
                result.getId(),
                result.getAccountId(),
                result.getStatus() != null ? result.getStatus().name() : null,
                result.getMetricsCaptured(),
                result.getErrorMessage());
    }
}
