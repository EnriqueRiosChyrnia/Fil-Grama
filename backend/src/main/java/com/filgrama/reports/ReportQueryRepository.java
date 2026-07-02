package com.filgrama.reports;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas propias del reporte (nivel cliente, cruzando todas sus cuentas y agrupando por red/tipo)
 * que los servicios per-cuenta del track D no exponen. Vive en el paquete dueño del track — NO toca
 * {@code com.filgrama.repository} ni el repo de D. <b>Toda consulta filtra por {@code client_id}</b>
 * (multi-tenant) y es de sólo lectura sobre {@code posts} / {@code post_metric_snapshots} (y
 * {@code account_metric_snapshots} sólo para detectar presencia de baseline del período anterior;
 * {@code audience_demographics} para el bloque "Público" v1.1 — spec/05 §v1.1 / research/06 §3).
 */
@Repository
public class ReportQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fila cruda de un post del cliente en el período (sin métrica; el ranking se resuelve aparte). */
    public record PostRow(
            Long id, Long accountId, String platform, String postType, String permalink,
            String caption, String remoteThumbnailUrl, Instant publishedAt, boolean ephemeral) {
    }

    /** Último valor de una métrica de un post dentro del rango (para el ranking). */
    public record PostMetricValue(Long postId, String metricKey, BigDecimal value) {
    }

    /** Par (red, metric_key) con al menos un snapshot de cuenta en un rango. */
    public record PlatformMetricKey(String platform, String metricKey) {
    }

    /**
     * Segmento de demografía agregado a nivel red: suma, por cuenta, el último valor capturado en el
     * rango de cada {@code (breakdown_type, breakdown_value)}, y luego suma esas cuentas entre sí
     * (mismo patrón "último valor por cuenta, sumado" que {@code SummaryService}).
     */
    public record DemographicRow(String platform, String breakdownType, String breakdownValue, BigDecimal value) {
    }

    /**
     * Un post con su reach y su engagement (últimos valores capturados) para el análisis de "mejores
     * horas/días" del MCP (spec/08). El bucketeo por hora/día en la timezone del cliente se hace en
     * Java desde {@code publishedAt} (fuente única, ver [[02-modelo-de-datos]]).
     */
    public record PostPerfRow(Long id, String platform, Instant publishedAt, BigDecimal reach, BigDecimal engagement) {
    }

    /**
     * (red, metric_key) que TIENEN al menos un snapshot de cuenta del cliente en {@code [from, to]}.
     * Permite distinguir "período previo ausente" (no aparece la clave → delta {@code null}) de
     * "previo = 0 real" (aparece la clave, aunque el valor sea 0 → delta real). Sin redes ⇒ vacío.
     * {@code accountIds} no vacío restringe el baseline a esas cuentas (reporte por cuenta, CU5).
     */
    public Set<PlatformMetricKey> accountMetricKeysPresent(Long clientId, Collection<String> platforms,
                                                           Collection<Long> accountIds,
                                                           LocalDate from, LocalDate to) {
        if (platforms == null || platforms.isEmpty()) {
            return Set.of();
        }
        boolean byAccount = accountIds != null && !accountIds.isEmpty();
        String sql = """
                SELECT DISTINCT a.platform AS platform, s.metric_key AS metric_key
                FROM account_metric_snapshots s
                JOIN social_accounts a ON a.id = s.account_id
                WHERE s.client_id = :clientId
                  AND a.platform IN (:platforms)
                  %s
                  AND (CAST(:fromDate AS date) IS NULL OR s.capture_date >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date)   IS NULL OR s.capture_date <= CAST(:toDate AS date))
                """.formatted(byAccount ? "AND s.account_id IN (:accountIds)" : "");
        MapSqlParameterSource params = baseParams(clientId, from, to).addValue("platforms", platforms);
        if (byAccount) {
            params.addValue("accountIds", accountIds);
        }
        return new HashSet<>(jdbc.query(sql, params, (rs, n) ->
                new PlatformMetricKey(rs.getString("platform"), rs.getString("metric_key"))));
    }

    /**
     * Posts del cliente publicados en {@code [from, to]} para las redes pedidas, del más nuevo al más
     * antiguo (orden por defecto de las secciones por tipo, CU5). Si no hay redes, devuelve vacío.
     * {@code accountIds} no vacío restringe los posts a esas cuentas (reporte por cuenta, CU5).
     */
    public List<PostRow> findPosts(Long clientId, LocalDate from, LocalDate to,
                                   Collection<String> platforms, Collection<Long> accountIds) {
        if (platforms == null || platforms.isEmpty()) {
            return List.of();
        }
        boolean byAccount = accountIds != null && !accountIds.isEmpty();
        String sql = """
                SELECT p.id, p.account_id, p.platform, p.post_type, p.permalink, p.caption,
                       p.remote_thumbnail_url, p.published_at, p.is_ephemeral
                FROM posts p
                WHERE p.client_id = :clientId
                  AND p.platform IN (:platforms)
                  %s
                  AND (CAST(:fromDate AS date) IS NULL OR p.published_at >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date)   IS NULL OR p.published_at <  (CAST(:toDate AS date) + 1))
                ORDER BY p.published_at DESC NULLS LAST, p.id DESC
                """.formatted(byAccount ? "AND p.account_id IN (:accountIds)" : "");
        MapSqlParameterSource params = baseParams(clientId, from, to).addValue("platforms", platforms);
        if (byAccount) {
            params.addValue("accountIds", accountIds);
        }
        return jdbc.query(sql, params, POST_ROW_MAPPER);
    }

    /**
     * Último valor (por {@code captured_at}) de cada métrica de cada post del conjunto, dentro del
     * rango. El llamador elige, por post, el {@code metric_key} resuelto según red/tipo y {@code rankBy}.
     */
    public List<PostMetricValue> latestPostMetrics(Long clientId, Collection<Long> postIds,
                                                   LocalDate from, LocalDate to) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT s.post_id,
                       s.metric_key,
                       (ARRAY_AGG(s.value ORDER BY s.captured_at DESC))[1] AS val
                FROM post_metric_snapshots s
                WHERE s.client_id = :clientId
                  AND s.post_id IN (:postIds)
                  AND (CAST(:fromDate AS date) IS NULL OR s.capture_date >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date)   IS NULL OR s.capture_date <= CAST(:toDate AS date))
                GROUP BY s.post_id, s.metric_key
                """;
        MapSqlParameterSource params = baseParams(clientId, from, to).addValue("postIds", postIds);
        return jdbc.query(sql, params, (rs, n) -> new PostMetricValue(
                rs.getLong("post_id"), rs.getString("metric_key"), rs.getBigDecimal("val")));
    }

    /**
     * Demografía de seguidores ({@code scope='FOLLOWER'}, research/06 §1) de las cuentas del cliente en
     * {@code [from, to]}, agregada por red + dimensión + segmento. Sin redes ⇒ vacío (misma convención
     * que {@link #findPosts}). {@code accountIds} no vacío restringe a esas cuentas (reporte por cuenta).
     */
    public List<DemographicRow> findDemographics(Long clientId, Collection<String> platforms,
                                                 Collection<Long> accountIds, LocalDate from, LocalDate to) {
        if (platforms == null || platforms.isEmpty()) {
            return List.of();
        }
        boolean byAccount = accountIds != null && !accountIds.isEmpty();
        String sql = """
                SELECT platform, breakdown_type, breakdown_value, SUM(latest_val) AS total
                FROM (
                    SELECT a.platform AS platform, d.account_id, d.breakdown_type, d.breakdown_value,
                           (ARRAY_AGG(d.value ORDER BY d.capture_date DESC))[1] AS latest_val
                    FROM audience_demographics d
                    JOIN social_accounts a ON a.id = d.account_id
                    WHERE d.client_id = :clientId
                      AND d.scope = 'FOLLOWER'
                      AND a.platform IN (:platforms)
                      %s
                      AND (CAST(:fromDate AS date) IS NULL OR d.capture_date >= CAST(:fromDate AS date))
                      AND (CAST(:toDate AS date)   IS NULL OR d.capture_date <= CAST(:toDate AS date))
                    GROUP BY a.platform, d.account_id, d.breakdown_type, d.breakdown_value
                ) per_account
                GROUP BY platform, breakdown_type, breakdown_value
                """.formatted(byAccount ? "AND d.account_id IN (:accountIds)" : "");
        MapSqlParameterSource params = baseParams(clientId, from, to).addValue("platforms", platforms);
        if (byAccount) {
            params.addValue("accountIds", accountIds);
        }
        return jdbc.query(sql, params, (rs, n) -> new DemographicRow(
                rs.getString("platform"), rs.getString("breakdown_type"), rs.getString("breakdown_value"),
                rs.getBigDecimal("total")));
    }

    /**
     * Todos los posts NO efímeros del cliente (toda la historia) con su reach y su engagement (último
     * valor capturado de cada uno). {@code reachKeys} = claves de alcance/visualizaciones por red;
     * {@code engagementKeys} = claves de interacción a sumar por post (el post solo tendrá las de su
     * red). Multi-tenant: filtra por {@code client_id}. El agrupado por hora/día lo hace el llamador.
     */
    public List<PostPerfRow> findPostPerformance(Long clientId, Collection<String> reachKeys,
                                                 Collection<String> engagementKeys) {
        String sql = """
                SELECT p.id, p.platform, p.published_at,
                       (SELECT (ARRAY_AGG(s.value ORDER BY s.captured_at DESC))[1]
                          FROM post_metric_snapshots s
                          WHERE s.post_id = p.id AND s.metric_key IN (:reachKeys)) AS reach,
                       (SELECT COALESCE(SUM(latest.v), 0) FROM (
                            SELECT (ARRAY_AGG(s.value ORDER BY s.captured_at DESC))[1] AS v
                            FROM post_metric_snapshots s
                            WHERE s.post_id = p.id AND s.metric_key IN (:engagementKeys)
                            GROUP BY s.metric_key) latest) AS engagement
                FROM posts p
                WHERE p.client_id = :clientId
                  AND p.published_at IS NOT NULL
                  AND COALESCE(p.is_ephemeral, FALSE) = FALSE
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("reachKeys", reachKeys)
                .addValue("engagementKeys", engagementKeys);
        return jdbc.query(sql, params, (rs, n) -> {
            OffsetDateTime published = rs.getObject("published_at", OffsetDateTime.class);
            return new PostPerfRow(
                    rs.getLong("id"),
                    rs.getString("platform"),
                    published == null ? null : published.toInstant(),
                    rs.getBigDecimal("reach"),
                    rs.getBigDecimal("engagement"));
        });
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
                rs.getString("post_type"),
                rs.getString("permalink"),
                rs.getString("caption"),
                rs.getString("remote_thumbnail_url"),
                published == null ? null : published.toInstant(),
                rs.getBoolean("is_ephemeral"));
    };
}
