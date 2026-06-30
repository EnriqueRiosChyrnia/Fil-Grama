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
import com.filgrama.sync.capture.dto.AudienceDemographicsCapture;
import com.filgrama.sync.capture.dto.DemographicSegment;

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

    /**
     * Upsert de los segmentos de demografía (v1.1) a {@code audience_demographics}. Formato largo: una
     * fila por segmento; re-run del día = upsert por la UNIQUE. No filtra por el catálogo de snapshots
     * (la demografía es su propia tabla); el gateo por capacidad lo hace el job antes de pedirla.
     * Devuelve cuántos segmentos se persistieron.
     */
    public int deriveDemographics(SocialAccount account, AudienceDemographicsCapture capture,
            Instant capturedAt, LocalDate captureDate) {
        int captured = 0;
        for (DemographicSegment seg : capture.segments()) {
            if (seg.value() == null) {
                continue;
            }
            upsert.upsertDemographic(account.getClientId(), account.getId(), seg.scope(),
                    seg.breakdownType(), seg.breakdownValue(), seg.value(), capturedAt, captureDate);
            captured++;
        }
        return captured;
    }
}
