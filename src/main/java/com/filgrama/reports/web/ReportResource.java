package com.filgrama.reports.web;

import java.time.Instant;

import com.filgrama.reports.Report;

/**
 * Recurso reporte devuelto por la API (spec/03): {@code {id, reportType, status, format, downloadUrl,
 * createdAt}}. El {@code downloadUrl} apunta al endpoint de descarga del propio cliente (multi-tenant).
 */
public record ReportResource(
        Long id,
        String reportType,
        String status,
        String format,
        String downloadUrl,
        Instant createdAt) {

    public static ReportResource from(Report r) {
        String downloadUrl = "/api/v1/clients/%d/reports/%d/download".formatted(r.getClientId(), r.getId());
        return new ReportResource(
                r.getId(),
                r.getReportType().name(),
                r.getStatus().name(),
                r.getFormat().name(),
                downloadUrl,
                r.getCreatedAt());
    }
}
