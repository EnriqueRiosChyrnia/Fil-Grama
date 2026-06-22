package com.filgrama.metrics.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Punto de una serie temporal: instante de captura + valor. */
public record SeriesPoint(Instant capturedAt, BigDecimal value) {
}
