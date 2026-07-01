package com.filgrama.sync.capture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un punto de una serie histórica devuelta por la API como {@code time_series} (una fila por día). */
public record DatedValue(LocalDate date, BigDecimal value) {
}
