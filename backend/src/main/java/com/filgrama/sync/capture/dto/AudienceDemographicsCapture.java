package com.filgrama.sync.capture.dto;

import java.util.List;

/**
 * Resultado de capturar la demografía de audiencia (v1.1, FG-T1). A diferencia de las métricas
 * normales (mapa {@code metric_key -> value}), la demografía es de formato largo: cada
 * {@link DemographicSegment} es una fila de {@code audience_demographics}.
 *
 * @param endpoint endpoint lógico consultado (se guarda en {@code raw_api_payloads.endpoint}).
 * @param rawJson  payload crudo (texto JSON) append-only; se persiste tal cual en jsonb.
 * @param segments segmentos derivados; vacío si la cuenta/red no soporta demografía o la API no la trajo.
 */
public record AudienceDemographicsCapture(String endpoint, String rawJson, List<DemographicSegment> segments) {
}
