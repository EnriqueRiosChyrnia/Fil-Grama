package com.filgrama.sync.capture.dto;

import java.math.BigDecimal;

/**
 * Un segmento de demografía de audiencia (una fila de {@code audience_demographics}).
 *
 * @param scope          audiencia: {@code FOLLOWER} | {@code REACHED} | {@code ENGAGED}.
 * @param breakdownType  dimensión: {@code AGE} | {@code GENDER} | {@code CITY} | {@code COUNTRY}.
 * @param breakdownValue valor del segmento (ej. {@code 25-34}, {@code F}, {@code Encarnación}, {@code PY}).
 * @param value          conteo del segmento.
 */
public record DemographicSegment(String scope, String breakdownType, String breakdownValue, BigDecimal value) {
}
