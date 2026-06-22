package com.filgrama.sync.capture.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resultado de capturar insights a nivel cuenta.
 *
 * @param endpoint endpoint lógico consultado (se guarda en {@code raw_api_payloads.endpoint}).
 * @param rawJson  payload crudo (texto JSON) append-only; se persiste tal cual en jsonb.
 * @param metrics  vista normalizada {@code metric_key -> value}; el provider traduce los nombres de
 *                 campo de cada API a las {@code metric_key} del catálogo. El derive filtra luego
 *                 por el catálogo CORE/ACTIVE.
 */
public record AccountCapture(String endpoint, String rawJson, Map<String, BigDecimal> metrics) {
}
