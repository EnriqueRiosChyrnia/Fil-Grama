package com.filgrama.mcp.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Confirmación de {@code save_report_narrative}: dónde quedó guardada la narrativa del período. */
public record NarrativeSaved(
        Long reportId,
        Long clientId,
        LocalDate from,
        LocalDate to,
        String source,
        String model,
        Instant generatedAt,
        boolean createdNewReport) {
}
