package com.filgrama.meta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del Data Deletion request en el formato <b>exacto</b> que exige Meta:
 * {@code {"url": "...", "confirmation_code": "..."}}. {@code url} es una página pública del front
 * que muestra el estado por código; {@code confirmation_code} es el id persistido para consultar.
 */
public record DataDeletionResponse(
        String url,
        @JsonProperty("confirmation_code") String confirmationCode) {
}
