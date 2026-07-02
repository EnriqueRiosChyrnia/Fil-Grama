package com.filgrama.mcp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.filgrama.reports.data.ReportData;

/**
 * Salida de {@code compare_periods}: dos períodos resumidos por red, con el valor de cada KPI en A y en
 * B y su variación ({@code delta = B − A}, {@code deltaPct}). No inventa causas: son solo los números.
 */
public record CompareView(
        ReportData.Client client,
        PeriodRef periodA,
        PeriodRef periodB,
        List<PlatformCompare> byPlatform) {

    public record PeriodRef(LocalDate from, LocalDate to) {
    }

    public record PlatformCompare(
            String platform,
            List<MetricCompare> metrics,
            BigDecimal engagementRateA,
            BigDecimal engagementRateB) {
    }

    /** Un KPI comparado entre A y B. {@code delta}/{@code deltaPct} null si falta B o A es 0. */
    public record MetricCompare(
            String key,
            String displayName,
            String unit,
            BigDecimal valueA,
            BigDecimal valueB,
            BigDecimal delta,
            BigDecimal deltaPct) {
    }
}
