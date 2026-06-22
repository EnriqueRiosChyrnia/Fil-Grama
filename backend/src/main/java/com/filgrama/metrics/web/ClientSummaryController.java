package com.filgrama.metrics.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.service.SummaryService;

/** {@code GET /api/v1/clients/{clientId}/summary} — KPIs agregados por red para el cliente. */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientSummaryController {

    private final SummaryService summaryService;

    public ClientSummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/{clientId}/summary")
    public SummaryResponse summary(
            @PathVariable Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform) {
        return summaryService.summary(clientId, from, to, platform);
    }
}
