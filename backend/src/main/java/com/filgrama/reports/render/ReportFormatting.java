package com.filgrama.reports.render;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formato de cifras del reporte, pensado para el <b>cliente final</b> (spec/07): números con
 * separador de miles, porcentajes legibles y deltas con signo. Idioma español. No calcula nada:
 * sólo presenta los valores que ya trae el {@code ReportData}.
 */
public final class ReportFormatting {

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final String EMPTY = "—";

    private ReportFormatting() {
    }

    /** Entero con separador de miles ({@code 12450 -> "12.450"}); null → guion. */
    public static String count(BigDecimal value) {
        if (value == null) {
            return EMPTY;
        }
        NumberFormat fmt = NumberFormat.getIntegerInstance(ES);
        return fmt.format(value.setScale(0, RoundingMode.HALF_UP));
    }

    /** Porcentaje a partir de una tasa ({@code 0.034 -> "3,4%"}); null → guion. */
    public static String percent(BigDecimal ratio) {
        if (ratio == null) {
            return EMPTY;
        }
        NumberFormat fmt = NumberFormat.getNumberInstance(ES);
        fmt.setMaximumFractionDigits(1);
        fmt.setMinimumFractionDigits(0);
        return fmt.format(ratio.multiply(BigDecimal.valueOf(100))) + "%";
    }

    /** Delta de un conteo con signo explícito ({@code +1.200} / {@code −350} / {@code 0}). */
    public static String signedCount(BigDecimal delta) {
        if (delta == null) {
            return EMPTY;
        }
        int sign = delta.signum();
        String body = count(delta.abs());
        return switch (sign) {
            case 1 -> "+" + body;
            case -1 -> "−" + body;
            default -> "0";
        };
    }

    /** Delta porcentual con signo ({@code +12,5%} / {@code −4%}); espera el % ya calculado. */
    public static String signedPercentPoints(BigDecimal pct) {
        if (pct == null) {
            return EMPTY;
        }
        NumberFormat fmt = NumberFormat.getNumberInstance(ES);
        fmt.setMaximumFractionDigits(1);
        fmt.setMinimumFractionDigits(0);
        int sign = pct.signum();
        String body = fmt.format(pct.abs()) + "%";
        return switch (sign) {
            case 1 -> "+" + body;
            case -1 -> "−" + body;
            default -> "0%";
        };
    }
}
