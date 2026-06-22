package com.filgrama.metrics.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.metrics.service.MetricCatalogService;

/** {@code GET /api/v1/metrics} — catálogo de métricas CORE/ACTIVE, filtrable por platform y level. */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricCatalogController {

    private final MetricCatalogService catalogService;

    public MetricCatalogController(MetricCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<MetricCatalogItem> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String level) {
        return catalogService.list(platform, level);
    }
}
