package com.filgrama.metrics.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.AccountReportResponse;
import com.filgrama.metrics.dto.MetricsReportRequest;
import com.filgrama.metrics.dto.PageResponse;
import com.filgrama.metrics.dto.PostListItem;
import com.filgrama.metrics.service.AccountPostsService;
import com.filgrama.metrics.service.MetricReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints de cuenta:
 *   {@code POST /api/v1/accounts/{id}/metrics:report} — informe de series (N métricas + rango).
 *   {@code GET  /api/v1/accounts/{id}/posts}          — página de posts (ordenable por métrica).
 *
 * <p>Custom method {@code :report} (Google AIP-136). El {@code PathPattern} de Spring matchea el
 * literal {@code metrics:report} como segmento, conviviendo con {@code GET /api/v1/metrics}.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Métricas")
public class AccountMetricsController {

    private final MetricReportService reportService;
    private final AccountPostsService postsService;

    public AccountMetricsController(MetricReportService reportService, AccountPostsService postsService) {
        this.reportService = reportService;
        this.postsService = postsService;
    }

    @Operation(summary = "Informe de series de una cuenta",
            description = "Patrón GA4 runReport: N métricas + rango en una request. Una serie por métrica. "
                    + "Métrica inválida → 400; from>to → 400; rango sin datos → serie con points vacío; "
                    + "cuenta inexistente → 404.")
    @PostMapping("/{id}/metrics:report")
    public AccountReportResponse report(@PathVariable Long id, @RequestBody(required = false) MetricsReportRequest request) {
        return reportService.accountReport(id, request);
    }

    @Operation(summary = "Página de posts de una cuenta")
    @GetMapping("/{id}/posts")
    public PageResponse<PostListItem> posts(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "published_at,desc") String sort) {
        return postsService.list(id, from, to, page, size, sort);
    }
}
