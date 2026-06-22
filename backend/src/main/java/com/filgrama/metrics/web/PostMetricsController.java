package com.filgrama.metrics.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.MetricsReportRequest;
import com.filgrama.metrics.dto.PostReportResponse;
import com.filgrama.metrics.service.MetricReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code POST /api/v1/posts/{id}/metrics:report} — informe de series de un post (mismo shape que el
 * de cuenta, con {@code postId}). Custom method {@code :report} (Google AIP-136).
 */
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "Métricas")
public class PostMetricsController {

    private final MetricReportService reportService;

    public PostMetricsController(MetricReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Informe de series de un post",
            description = "N métricas + rango en una request. Métrica inválida → 400; from>to → 400; "
                    + "rango sin datos → serie con points vacío; post inexistente → 404.")
    @PostMapping("/{id}/metrics:report")
    public PostReportResponse report(@PathVariable Long id, @RequestBody(required = false) MetricsReportRequest request) {
        return reportService.postReport(id, request);
    }
}
