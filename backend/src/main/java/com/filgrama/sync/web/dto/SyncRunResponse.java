package com.filgrama.sync.web.dto;

import java.time.Instant;

import com.filgrama.domain.SyncRun;

/** Vista de una corrida del job. Nunca expone tokens ni datos sensibles. */
public record SyncRunResponse(
        Long id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        Integer accountsTotal,
        Integer accountsOk,
        Integer accountsFailed,
        String errorSummary) {

    public static SyncRunResponse from(SyncRun run) {
        return new SyncRunResponse(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getAccountsTotal(),
                run.getAccountsOk(),
                run.getAccountsFailed(),
                run.getErrorSummary());
    }
}
