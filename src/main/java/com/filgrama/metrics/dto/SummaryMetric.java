package com.filgrama.metrics.dto;

import java.math.BigDecimal;

/**
 * KPI agregado de una métrica de cuenta en el período.
 * {@code latest} = suma del último valor por cuenta; {@code total} = suma de todos los valores del período.
 */
public record SummaryMetric(
        String metric,
        String displayName,
        String unit,
        BigDecimal latest,
        BigDecimal total) {
}
