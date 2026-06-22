package com.filgrama.metrics.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.PostSeriesResponse;
import com.filgrama.metrics.service.MetricSeriesService;

/** {@code GET /api/v1/posts/{id}/metrics} — serie temporal de una métrica de un post. */
@RestController
@RequestMapping("/api/v1/posts")
public class PostMetricsController {

    private final MetricSeriesService seriesService;

    public PostMetricsController(MetricSeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @GetMapping("/{id}/metrics")
    public PostSeriesResponse metrics(
            @PathVariable Long id,
            @RequestParam String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return seriesService.postSeries(id, metric, from, to);
    }
}
