package com.filgrama.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Utilidades compartidas del track Métricas: limpieza de números y filtro de rango de fechas. */
public final class MetricFormat {

    private MetricFormat() {
    }

    /**
     * Normaliza un {@link BigDecimal} de NUMERIC(20,4) a su forma mínima para el JSON:
     * {@code 12450.0000 -> 12450}, {@code 0.0340 -> 0.034}. Evita la notación científica que
     * dejaría {@code stripTrailingZeros()} con escala negativa.
     */
    public static BigDecimal clean(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /** {@code true} si {@code date} cae dentro de [from, to] (límites null = abiertos). */
    public static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }
}
