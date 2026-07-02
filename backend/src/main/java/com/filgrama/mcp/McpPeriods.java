package com.filgrama.mcp;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import com.filgrama.error.ApiException;

/**
 * Parseo del parámetro {@code period} de las tools (spec/08): un mes {@code YYYY-MM} (se expande al
 * rango del mes completo) o un rango explícito {@code YYYY-MM-DD..YYYY-MM-DD}. Cualquier otra cosa es
 * un error de validación claro para que Claude reintente con el formato correcto.
 */
final class McpPeriods {

    /** Rango cerrado {@code [from, to]} que consumen el assembler y las queries de reporte. */
    record Range(LocalDate from, LocalDate to) {
    }

    static Range parse(String period) {
        if (period == null || period.isBlank()) {
            throw ApiException.badRequest(
                    "'period' es obligatorio: usá un mes 'YYYY-MM' o un rango 'YYYY-MM-DD..YYYY-MM-DD'");
        }
        String value = period.trim();
        try {
            if (value.contains("..")) {
                String[] parts = value.split("\\.\\.", 2);
                LocalDate from = LocalDate.parse(parts[0].trim());
                LocalDate to = LocalDate.parse(parts[1].trim());
                if (from.isAfter(to)) {
                    throw ApiException.badRequest(
                            "rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
                }
                return new Range(from, to);
            }
            YearMonth month = YearMonth.parse(value);
            return new Range(month.atDay(1), month.atEndOfMonth());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(
                    "'period' inválido: '%s'. Usá un mes 'YYYY-MM' o un rango 'YYYY-MM-DD..YYYY-MM-DD'"
                            .formatted(period));
        }
    }

    private McpPeriods() {
    }
}
