package com.filgrama.sync.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.domain.SyncRun;
import com.filgrama.error.ApiException;
import com.filgrama.repository.SyncAccountResultRepository;
import com.filgrama.repository.SyncRunRepository;
import com.filgrama.sync.job.SyncService;
import com.filgrama.sync.web.dto.PageResponse;
import com.filgrama.sync.web.dto.SyncAccountResultResponse;
import com.filgrama.sync.web.dto.SyncRunDetailResponse;
import com.filgrama.sync.web.dto.SyncRunResponse;
import com.filgrama.sync.web.dto.SyncRunTriggerResponse;

/**
 * Endpoints {@code [ADMIN]} del job de captura (base {@code /api/v1}, contratos spec/03).
 * Toda la clase exige rol ADMIN ({@code @EnableMethodSecurity} activo en SecurityConfig).
 * Nunca expone tokens. Errores RFC 7807 vía el handler compartido ({@code com.filgrama.error}).
 */
@RestController
@RequestMapping("/api/v1/sync")
@PreAuthorize("hasRole('ADMIN')")
public class SyncController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SyncService syncService;
    private final SyncRunRepository runRepository;
    private final SyncAccountResultRepository resultRepository;

    public SyncController(SyncService syncService, SyncRunRepository runRepository,
            SyncAccountResultRepository resultRepository) {
        this.syncService = syncService;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    /** Dispara una corrida y devuelve {@code 202 {runId}}. */
    @PostMapping("/run")
    public ResponseEntity<SyncRunTriggerResponse> run() {
        Long runId = syncService.runOnce();
        return ResponseEntity.accepted().body(new SyncRunTriggerResponse(runId));
    }

    /** Historial paginado de corridas, más recientes primero. */
    @GetMapping("/runs")
    public PageResponse<SyncRunResponse> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<SyncRunResponse> result = runRepository.findAll(pageable).map(SyncRunResponse::from);
        return PageResponse.of(result);
    }

    /** Detalle de una corrida + sus {@code sync_account_results}. {@code 404} si no existe. */
    @GetMapping("/runs/{id}")
    public SyncRunDetailResponse runDetail(@PathVariable Long id) {
        SyncRun run = runRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Corrida " + id + " no existe"));
        List<SyncAccountResultResponse> accounts = resultRepository.findByRunId(id).stream()
                .map(SyncAccountResultResponse::from)
                .toList();
        return new SyncRunDetailResponse(SyncRunResponse.from(run), accounts);
    }
}
