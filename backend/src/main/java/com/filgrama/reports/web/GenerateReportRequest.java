package com.filgrama.reports.web;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportType;

import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /api/v1/clients/{clientId}/reports} (contrato spec/03).
 * {@code platforms}, {@code accountIds} y {@code rankBy} son opcionales: sin redes se toman todas las
 * del cliente; sin {@code rankBy} se usa {@code reach}. Si viene {@code accountIds}, el reporte se arma
 * SÓLO con esas cuentas (deben ser del cliente) y la red se deriva de ellas — tiene prioridad sobre
 * {@code platforms}. La validación de campos faltantes la mapea el handler a 400.
 */
public record GenerateReportRequest(
        @NotNull ReportType reportType,
        @NotNull ReportFormat format,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate from,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate to,
        List<String> platforms,
        List<Long> accountIds,
        String rankBy) {
}
