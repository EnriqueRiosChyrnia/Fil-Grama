package com.filgrama.reports.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.reports.Report;
import com.filgrama.reports.ReportService;
import com.filgrama.reports.ReportService.DownloadPayload;

import jakarta.validation.Valid;

/**
 * Endpoints de reportes (spec/03, base {@code /api/v1}). Requieren auth (protegidos por defecto en el
 * {@code SecurityConfig} del track A). El {@code created_by} sale del {@code SecurityContext}
 * (principal = userId). Multi-tenant: el {@code clientId} de la ruta filtra todo; un reporte de otro
 * cliente da 404.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Genera el reporte (síncrono) → 201 + recurso. */
    @PostMapping
    public ResponseEntity<ReportResource> generate(
            @PathVariable Long clientId,
            @Valid @RequestBody GenerateReportRequest request,
            @AuthenticationPrincipal Long userId) {
        Report report = reportService.generate(clientId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResource.from(report));
    }

    /** Metadatos del reporte. */
    @GetMapping("/{reportId}")
    public ReportResource get(@PathVariable Long clientId, @PathVariable Long reportId) {
        return ReportResource.from(reportService.get(clientId, reportId));
    }

    /** Descarga el archivo generado con su Content-Type y Content-Disposition. */
    @GetMapping("/{reportId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long clientId, @PathVariable Long reportId) {
        DownloadPayload payload = reportService.download(clientId, reportId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(payload.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(payload.content());
    }
}
