package com.filgrama.reports.web;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.filgrama.reports.ReportType;

import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /api/v1/clients/{clientId}/reports:preview} (contrato spec/03). Es el de
 * {@code POST /reports} <b>sin {@code format}</b>: la vista previa devuelve sólo los datos
 * ({@link com.filgrama.reports.data.ReportData}), no genera ni persiste archivo, así que el formato
 * de export no aplica. {@code platforms}, {@code accountIds} y {@code rankBy} son opcionales (sin
 * redes, todas las del cliente; sin {@code rankBy}, {@code reach}). Si viene {@code accountIds}, la
 * vista previa se arma SÓLO con esas cuentas (mismo armado que el export). Campos faltantes → 400.
 */
public record PreviewReportRequest(
        @NotNull ReportType reportType,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate from,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate to,
        List<String> platforms,
        List<Long> accountIds,
        String rankBy) {
}
