package com.filgrama.sync.web.dto;

import java.util.List;

/** Detalle de una corrida + sus resultados por cuenta ({@code GET /sync/runs/{id}}). */
public record SyncRunDetailResponse(SyncRunResponse run, List<SyncAccountResultResponse> accounts) {
}
