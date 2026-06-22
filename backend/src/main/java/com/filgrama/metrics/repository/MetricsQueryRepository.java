package com.filgrama.metrics.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas custom del track Métricas (agregados y orden por métrica). Vive en el paquete del
 * track — NO toca {@code com.filgrama.repository}. Usa {@link NamedParameterJdbcTemplate} sobre las
 * tablas append-only. <b>Toda consulta filtra por {@code client_id}</b> (multi-tenant).
 */
@Repository
public class MetricsQueryRepository {

    /** Columnas permitidas para ordenar posts (anti-inyección en el ORDER BY dinámico). */
    private static final Set<String> SORTABLE_POST_COLUMNS = Set.of("published_at", "first_seen_at");

    private final NamedParameterJdbcTemplate jdbc;

    public MetricsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Agregado de una métrica de cuenta (level ACCOUNT) por cliente + red en el rango. */
    public record MetricAgg(BigDecimal total, BigDecimal latest, BigDecimal earliest) {
    }

    /** Fila de post para la página rankeable. {@code sortValue} sólo se setea al ordenar por métrica. */
    public record PostRow(
            Long id, Long accountId, String platform, String externalPostId, String postType,
            String permalink, String caption, String remoteThumbnailUrl, Instant publishedAt,
            BigDecimal sortValue) {
    }

    /**
     * Agrega una métrica de cuenta del cliente para una red en el rango:
     * total (suma de todos los snapshots), latest (suma del último por cuenta) y
     * earliest (suma del primero por cuenta). El último/primero por cuenta usan {@code captured_at}.
     */
    public MetricAgg accountMetricAgg(Long clientId, String platform, String metricKey,
                                      LocalDate from, LocalDate to) {
        String sql = """
                SELECT COALESCE(SUM(per.total_val), 0)    AS total,
                       COALESCE(SUM(per.latest_val), 0)   AS latest,
                       COALESCE(SUM(per.earliest_val), 0) AS earliest
                FROM (
                    SELECT s.account_id,
                           SUM(s.value)                                          AS total_val,
                           (ARRAY_AGG(s.value ORDER BY s.captured_at DESC))[1]   AS latest_val,
                           (ARRAY_AGG(s.value ORDER BY s.captured_at ASC))[1]    AS earliest_val
                    FROM account_metric_snapshots s
                    JOIN social_accounts a ON a.id = s.account_id
                    WHERE s.client_id = :clientId
                      AND a.platform = :platform
                      AND s.metric_key = :metricKey
                      AND (CAST(:fromDate AS date) IS NULL OR s.capture_date >= CAST(:fromDate AS date))
                      AND (CAST(:toDate AS date)   IS NULL OR s.capture_date <= CAST(:toDate AS date))
                    GROUP BY s.account_id
                ) per
                """;
        MapSqlParameterSource params = baseParams(clientId, from, to)
                .addValue("platform", platform)
                .addValue("metricKey", metricKey);
        return jdbc.queryForObject(sql, params, (rs, n) -> new MetricAgg(
                rs.getBigDecimal("total"), rs.getBigDecimal("latest"), rs.getBigDecimal("earliest")));
    }

    /** Cuenta los posts de la cuenta (del cliente) publicados en el rango. */
    public long countAccountPosts(Long clientId, Long accountId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*)
                FROM posts p
                WHERE p.client_id = :clientId
                  AND p.account_id = :accountId
                  AND (CAST(:fromDate AS date) IS NULL OR p.published_at >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date)   IS NULL OR p.published_at <  (CAST(:toDate AS date) + 1))
                """;
        MapSqlParameterSource params = baseParams(clientId, from, to).addValue("accountId", accountId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    /** Página de posts ordenada por una columna del propio post ({@code published_at}/{@code first_seen_at}). */
    public List<PostRow> findAccountPostsByColumn(Long clientId, Long accountId, LocalDate from,
                                                  LocalDate to, String column, boolean asc,
                                                  int limit, int offset) {
        if (!SORTABLE_POST_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("Columna de orden no permitida: " + column);
        }
        String sql = """
                SELECT p.id, p.account_id, p.platform, p.external_post_id, p.post_type,
                       p.permalink, p.caption, p.remote_thumbnail_url, p.published_at,
                       NULL::numeric AS sort_value
                FROM posts p
                WHERE p.client_id = :clientId
                  AND p.account_id = :accountId
                  AND (CAST(:fromDate AS date) IS NULL OR p.published_at >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date)   IS NULL OR p.published_at <  (CAST(:toDate AS date) + 1))
                ORDER BY p.%s %s NULLS LAST
                LIMIT :limit OFFSET :offset
                """.formatted(column, asc ? "ASC" : "DESC");
        MapSqlParameterSource params = baseParams(clientId, from, to)
                .addValue("accountId", accountId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, POST_ROW_MAPPER);
    }

    /**
     * Página de posts ordenada por el último valor de una métrica de post en el rango (top posts).
     * Une cada post con el snapshot más reciente de {@code metricKey} dentro del rango.
     */
    public List<PostRow> findAccountPostsByMetric(Long clientId, Long accountId, LocalDate from,
                                                  LocalDate to, String metricKey, boolean asc,
                                                  int limit, int offset) {
        String sql = """
                SELECT p.id, p.account_id, p.platform, p.external_post_id, p.post_type,
                       p.permalink, p.caption, p.remote_thumbnail_url, p.published_at,
                       ms.val AS sort_value
                FROM posts p
                LEFT JOIN LATERAL (
                    SELECT s.value AS val
                    FROM post_metric_snapshots s
                    WHERE s.post_id = p.id
                      AND s.client_id = :clientId
                      AND s.metric_key = :metricKey
                      AND (CAST(:fromDate AS date) IS NULL OR s.capture_date >= CAST(:fromDate AS date))
                      AND (CAST(:toDate AS date)   IS NULL OR s.capture_date <= CAST(:toDate AS date))
                    ORDER BY s.captured_at DESC
                    LIMIT 1
                ) ms ON TRUE
                WHERE p.client_id = :clientId
                  AND p.account_id = :accountId
                  AND (CAST(:fromDate AS date) IS NULL OR p.published_at >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date)   IS NULL OR p.published_at <  (CAST(:toDate AS date) + 1))
                ORDER BY ms.val %s NULLS LAST, p.published_at DESC
                LIMIT :limit OFFSET :offset
                """.formatted(asc ? "ASC" : "DESC");
        MapSqlParameterSource params = baseParams(clientId, from, to)
                .addValue("accountId", accountId)
                .addValue("metricKey", metricKey)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, POST_ROW_MAPPER);
    }

    private static MapSqlParameterSource baseParams(Long clientId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("fromDate", from, Types.DATE)
                .addValue("toDate", to, Types.DATE);
    }

    private static final RowMapper<PostRow> POST_ROW_MAPPER = (rs, n) -> {
        OffsetDateTime published = rs.getObject("published_at", OffsetDateTime.class);
        return new PostRow(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("platform"),
                rs.getString("external_post_id"),
                rs.getString("post_type"),
                rs.getString("permalink"),
                rs.getString("caption"),
                rs.getString("remote_thumbnail_url"),
                published == null ? null : published.toInstant(),
                rs.getBigDecimal("sort_value"));
    };
}
