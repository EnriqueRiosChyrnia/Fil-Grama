package com.filgrama.mcp.dto;

import java.math.BigDecimal;
import java.util.List;

import com.filgrama.reports.data.ReportData;

/**
 * Salida de {@code get_posting_performance}: rendimiento promedio de las publicaciones agrupadas por
 * hora del día o día de la semana (en la timezone del cliente), sobre toda la historia → "mejores
 * horas/días para publicar". El reach y el engagement son promedios por post dentro del bucket.
 */
public record PostingPerformanceView(
        ReportData.Client client,
        String by,
        List<Bucket> buckets) {

    /**
     * Un bucket temporal. {@code label} es legible ("13:00" por hora; "lunes" por día). {@code order}
     * fija el orden natural (0-23 por hora; 1=lunes..7=domingo). Promedios null si no hubo dato.
     */
    public record Bucket(
            String label,
            int order,
            int postCount,
            BigDecimal avgReach,
            BigDecimal avgEngagement) {
    }
}
