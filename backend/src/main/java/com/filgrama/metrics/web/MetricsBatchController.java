package com.filgrama.metrics.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.BatchReportRequest;
import com.filgrama.metrics.dto.BatchReportResponse;
import com.filgrama.metrics.service.MetricReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code POST /api/v1/metrics:batchReport} — varios informes en una sola llamada (espejo de
 * {@code batchRunReports} de GA4). Controller propio (no cuelga de {@code /api/v1/metrics} para que
 * el literal {@code metrics:batchReport} sea un segmento exacto, sin {@code /} espurio).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Métricas")
public class MetricsBatchController {

    private final MetricReportService reportService;

    public MetricsBatchController(MetricReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Batch de informes de cuenta",
            description = "Hasta 20 informes en una request; la response conserva el orden de 'requests'. "
                    + "Atómico: cuenta inexistente → 404 de todo el batch; métrica inválida → 400; "
                    + ">20 requests → 400.")
    @PostMapping("/metrics:batchReport")
    public BatchReportResponse batchReport(@RequestBody(required = false) BatchReportRequest request) {
        return reportService.batchReport(request);
    }
}
