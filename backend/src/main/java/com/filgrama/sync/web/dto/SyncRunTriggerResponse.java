package com.filgrama.sync.web.dto;

/** Respuesta de {@code POST /sync/run}: el id de la corrida creada. */
public record SyncRunTriggerResponse(Long runId) {
}
