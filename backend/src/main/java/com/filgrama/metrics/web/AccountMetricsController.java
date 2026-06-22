package com.filgrama.metrics.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.AccountSeriesResponse;
import com.filgrama.metrics.dto.PageResponse;
import com.filgrama.metrics.dto.PostListItem;
import com.filgrama.metrics.service.AccountPostsService;
import com.filgrama.metrics.service.MetricSeriesService;

/**
 * Endpoints de cuenta:
 *   {@code GET /api/v1/accounts/{id}/metrics} — serie temporal de una métrica de cuenta.
 *   {@code GET /api/v1/accounts/{id}/posts}   — página de posts (ordenable por métrica).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountMetricsController {

    private final MetricSeriesService seriesService;
    private final AccountPostsService postsService;

    public AccountMetricsController(MetricSeriesService seriesService, AccountPostsService postsService) {
        this.seriesService = seriesService;
        this.postsService = postsService;
    }

    @GetMapping("/{id}/metrics")
    public AccountSeriesResponse metrics(
            @PathVariable Long id,
            @RequestParam String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String granularity) {
        return seriesService.accountSeries(id, metric, from, to, granularity);
    }

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
