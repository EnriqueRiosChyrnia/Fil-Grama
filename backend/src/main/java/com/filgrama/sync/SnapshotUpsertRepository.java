package com.filgrama.sync;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Upsert idempotente de snapshots con {@code ON CONFLICT}. Repo propio del track (JdbcTemplate +
 * native query) para NO tocar los repos compartidos de {@code com.filgrama.repository}.
 *
 * <p><b>Idempotencia diaria — "último valor del día gana":</b> reintentar el mismo día sobre
 * {@code (account_id|post_id, metric_key, capture_date)} actualiza el valor en vez de duplicar la
 * fila. La serie histórica (una fila inmutable por día hacia el futuro) queda intacta; el crudo
 * ({@code raw_api_payloads}) sí es append puro y se guarda aparte.
 */
@Repository
public class SnapshotUpsertRepository {

    private static final String ACCOUNT_UPSERT = """
            INSERT INTO account_metric_snapshots
                (client_id, account_id, metric_key, value, period, captured_at, capture_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (account_id, metric_key, capture_date)
            DO UPDATE SET value = EXCLUDED.value,
                          period = EXCLUDED.period,
                          captured_at = EXCLUDED.captured_at
            """;

    private static final String POST_UPSERT = """
            INSERT INTO post_metric_snapshots
                (client_id, account_id, post_id, metric_key, value, captured_at, capture_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (post_id, metric_key, capture_date)
            DO UPDATE SET value = EXCLUDED.value,
                          captured_at = EXCLUDED.captured_at
            """;

    private final JdbcTemplate jdbc;

    public SnapshotUpsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertAccountSnapshot(Long clientId, Long accountId, String metricKey,
            BigDecimal value, String period, Instant capturedAt, LocalDate captureDate) {
        jdbc.update(ACCOUNT_UPSERT, clientId, accountId, metricKey, value, period,
                OffsetDateTime.ofInstant(capturedAt, ZoneOffset.UTC), captureDate);
    }

    public void upsertPostSnapshot(Long clientId, Long accountId, Long postId, String metricKey,
            BigDecimal value, Instant capturedAt, LocalDate captureDate) {
        jdbc.update(POST_UPSERT, clientId, accountId, postId, metricKey, value,
                OffsetDateTime.ofInstant(capturedAt, ZoneOffset.UTC), captureDate);
    }
}
