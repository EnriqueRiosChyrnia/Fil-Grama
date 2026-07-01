package com.filgrama.sync.capture.dto;

import java.util.List;

/**
 * Resultado de capturar {@code reach} de cuenta como {@code time_series} (FG-CS-CAP): la única
 * métrica de cuenta con serie propia en Meta (spec/05). Un {@link DatedValue} por día del rango
 * pedido — el deriver hace upsert de <b>una fila por fecha</b> (no una sola fila "de hoy").
 *
 * @param endpoint endpoint lógico consultado (se guarda en {@code raw_api_payloads.endpoint}).
 * @param rawJson  payload crudo (texto JSON) append-only; se persiste tal cual en jsonb.
 * @param values   un punto por día devuelto por la API, en el orden de la respuesta.
 */
public record AccountReachSeriesCapture(String endpoint, String rawJson, List<DatedValue> values) {
}
