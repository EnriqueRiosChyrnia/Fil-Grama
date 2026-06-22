package com.filgrama.sync.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker 24/7: dispara la corrida diaria por cron (hora baja, default 03:00). La propiedad
 * {@code sync.cron} la fija la central en application.yml; acá se lee con default vía {@code @Value}.
 * Se desactiva en tests con {@code sync.scheduler.enabled=false} (no se crea el bean).
 * Un fallo de la corrida se loguea, nunca propaga (no debe tumbar el scheduler).
 */
@Component
@ConditionalOnProperty(name = "sync.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;

    public SyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${sync.cron:0 0 3 * * *}")
    public void runDaily() {
        try {
            Long runId = syncService.runOnce();
            log.info("Corrida diaria programada completada: runId={}", runId);
        } catch (RuntimeException e) {
            log.error("Corrida diaria programada falló", e);
        }
    }
}
