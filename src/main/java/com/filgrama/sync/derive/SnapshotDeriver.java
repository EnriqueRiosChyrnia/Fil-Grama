package com.filgrama.sync.derive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.filgrama.domain.Post;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.sync.SnapshotUpsertRepository;
import com.filgrama.sync.capture.dto.AccountCapture;

/**
 * Deriva los payloads normalizados ({@code metric_key -> value}) a filas de snapshot, filtrando por
 * el catálogo CORE/ACTIVE y haciendo upsert idempotente. {@code captureDate} ya viene calculado en
 * la timezone del cliente; {@code capturedAt} = ahora.
 */
@Component
public class SnapshotDeriver {

    private final MetricCatalog catalog;
    private final SnapshotUpsertRepository upsert;

    public SnapshotDeriver(MetricCatalog catalog, SnapshotUpsertRepository upsert) {
        this.catalog = catalog;
        this.upsert = upsert;
    }

    /** Upsert de las métricas de cuenta del catálogo. Devuelve cuántas se capturaron. */
    public int deriveAccount(SocialAccount account, AccountCapture capture,
            Instant capturedAt, LocalDate captureDate) {
        Set<String> allowed = catalog.coreActiveKeys(account.getPlatform(), MetricLevel.ACCOUNT);
        int captured = 0;
        for (Map.Entry<String, BigDecimal> e : capture.metrics().entrySet()) {
            if (e.getValue() == null || !allowed.contains(e.getKey())) {
                continue;
            }
            upsert.upsertAccountSnapshot(account.getClientId(), account.getId(),
                    e.getKey(), e.getValue(), null, capturedAt, captureDate);
            captured++;
        }
        return captured;
    }

    /**
     * Upsert de las métricas de un post (sirve igual para stories: sus {@code ig_story_*} son de
     * nivel POST en el catálogo). Devuelve cuántas se capturaron.
     */
    public int derivePost(SocialAccount account, Post post, Map<String, BigDecimal> metrics,
            Instant capturedAt, LocalDate captureDate) {
        Set<String> allowed = catalog.coreActiveKeys(account.getPlatform(), MetricLevel.POST);
        int captured = 0;
        for (Map.Entry<String, BigDecimal> e : metrics.entrySet()) {
            if (e.getValue() == null || !allowed.contains(e.getKey())) {
                continue;
            }
            upsert.upsertPostSnapshot(account.getClientId(), account.getId(), post.getId(),
                    e.getKey(), e.getValue(), capturedAt, captureDate);
            captured++;
        }
        return captured;
    }
}
