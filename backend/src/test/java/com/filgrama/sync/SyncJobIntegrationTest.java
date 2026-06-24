package com.filgrama.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.AccountMetricSnapshot;
import com.filgrama.domain.Post;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.SyncAccountResult;
import com.filgrama.domain.SyncRun;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.domain.enums.SyncAccountStatus;
import com.filgrama.domain.enums.SyncRunStatus;

/**
 * Tests del job contra Postgres real (Testcontainers) con {@link com.filgrama.sync.capture.MockInsightsProvider}.
 * Cubre la "definición de terminado": idempotencia, append-only hacia el futuro, tolerancia a
 * fallos, upsert de posts sin duplicar, refresh de token y captura de stories con miniatura.
 */
class SyncJobIntegrationTest extends SyncTestSupport {

    private static final ZoneId ASU = ZoneId.of("America/Asuncion");

    private long countAccountSnapshots(Long accountId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM account_metric_snapshots WHERE account_id = ?", Long.class, accountId);
    }

    private long countPostSnapshots(Long accountId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post_metric_snapshots WHERE account_id = ?", Long.class, accountId);
    }

    @Test
    void idempotencia_rerun_mismo_dia_no_duplica_y_ultimo_gana() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "molinos_ig");
        LocalDate today = LocalDate.now(ASU);

        mockProvider.setSeed(1_000L);
        Long run1 = syncService.runOnce();

        assertThat(syncRunRepository.findById(run1).orElseThrow().getStatus()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(countAccountSnapshots(account.getId())).isEqualTo(5);   // 5 métricas CORE de cuenta IG
        assertThat(countPostSnapshots(account.getId())).isEqualTo(16);     // 7+7 (2 posts) + 2 (story)
        assertThat(postRepository.findByAccountId(account.getId())).hasSize(3); // p1, p2, story

        BigDecimal reachV1 = accountSnapshotRepository
                .findByAccountIdAndMetricKeyAndCaptureDate(account.getId(), "ig_reach", today)
                .orElseThrow().getValue();
        int rawAfterRun1 = rawApiPayloadRepository.findByAccountId(account.getId()).size();
        assertThat(rawAfterRun1).isEqualTo(5); // ACCOUNT + POSTS_LIST + 2*POST + 1 story POST

        // Segundo run el mismo día, con valores nuevos del "API".
        mockProvider.setSeed(2_000L);
        syncService.runOnce();

        // No duplica snapshots: una fila por (cuenta, métrica, día).
        assertThat(countAccountSnapshots(account.getId())).isEqualTo(5);
        assertThat(countPostSnapshots(account.getId())).isEqualTo(16);
        assertThat(postRepository.findByAccountId(account.getId())).hasSize(3);

        List<AccountMetricSnapshot> reachRows = accountSnapshotRepository
                .findByAccountIdAndMetricKeyOrderByCapturedAtAsc(account.getId(), "ig_reach");
        assertThat(reachRows).hasSize(1);
        assertThat(reachRows.get(0).getValue()).isNotEqualByComparingTo(reachV1); // último valor del día gana

        // El crudo SÍ es append puro: se duplican las filas.
        assertThat(rawApiPayloadRepository.findByAccountId(account.getId())).hasSize(rawAfterRun1 * 2);
    }

    @Test
    void append_only_hacia_el_futuro_no_toca_dias_pasados() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "histo_ig");
        LocalDate today = LocalDate.now(ASU);
        LocalDate yesterday = today.minusDays(1);
        Instant yesterdayInstant = yesterday.atStartOfDay(ASU).toInstant();

        // Fila histórica del día anterior.
        upsertRepository.upsertAccountSnapshot(client.getId(), account.getId(), "ig_reach",
                new BigDecimal("999"), null, yesterdayInstant, yesterday);

        syncService.runOnce();

        // El día pasado queda intacto (nunca UPDATE/DELETE).
        assertThat(accountSnapshotRepository
                .findByAccountIdAndMetricKeyAndCaptureDate(account.getId(), "ig_reach", yesterday)
                .orElseThrow().getValue()).isEqualByComparingTo("999");

        // Hay una fila nueva para hoy con otro valor.
        var todayRow = accountSnapshotRepository
                .findByAccountIdAndMetricKeyAndCaptureDate(account.getId(), "ig_reach", today);
        assertThat(todayRow).isPresent();
        assertThat(todayRow.get().getValue()).isNotEqualByComparingTo("999");

        // Días distintos = filas distintas.
        assertThat(accountSnapshotRepository
                .findByAccountIdAndMetricKeyOrderByCapturedAtAsc(account.getId(), "ig_reach")).hasSize(2);
    }

    @Test
    void tolerancia_a_fallos_una_cuenta_error_no_tumba_la_corrida() {
        var client = newClient("America/Asuncion");
        SocialAccount good = connectAccount(client.getId(), Platform.INSTAGRAM, "buena_ig");
        SocialAccount bad = connectAccount(client.getId(), Platform.INSTAGRAM, "boom_ig"); // sentinela de fallo

        Long runId = syncService.runOnce();

        SyncRun run = syncRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.PARTIAL);
        assertThat(run.getAccountsTotal()).isEqualTo(2);
        assertThat(run.getAccountsOk()).isEqualTo(1);
        assertThat(run.getAccountsFailed()).isEqualTo(1);
        assertThat(run.getErrorSummary()).contains("cuenta " + bad.getId());

        List<SyncAccountResult> results = syncAccountResultRepository.findByRunId(runId);
        assertThat(results).hasSize(2);
        SyncAccountResult goodResult = results.stream()
                .filter(r -> r.getAccountId().equals(good.getId())).findFirst().orElseThrow();
        SyncAccountResult badResult = results.stream()
                .filter(r -> r.getAccountId().equals(bad.getId())).findFirst().orElseThrow();

        assertThat(goodResult.getStatus()).isEqualTo(SyncAccountStatus.OK);
        assertThat(goodResult.getMetricsCaptured()).isPositive();
        assertThat(badResult.getStatus()).isEqualTo(SyncAccountStatus.ERROR);
        assertThat(badResult.getErrorMessage()).contains("fallo simulado");

        // La cuenta OK persiste; la que falló se revierte por completo (atómico por cuenta).
        assertThat(countAccountSnapshots(good.getId())).isEqualTo(5);
        assertThat(countAccountSnapshots(bad.getId())).isZero();
        assertThat(rawApiPayloadRepository.findByAccountId(bad.getId())).isEmpty();
    }

    @Test
    void refresca_token_proximo_a_expirar_antes_de_consultar() {
        var client = newClient("America/Asuncion");
        // Vence en 1h → dentro del buffer (24h) → debe refrescar.
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "refresh_ig",
                Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(accountCredentialRepository.findById(account.getId()).orElseThrow()
                .getLastRefreshedAt()).isNull();

        Long runId = syncService.runOnce();

        assertThat(syncRunRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(SyncRunStatus.SUCCESS);
        AccountCredential after = accountCredentialRepository.findById(account.getId()).orElseThrow();
        assertThat(after.getLastRefreshedAt()).isNotNull();
    }

    @Test
    void captura_story_de_instagram_y_cachea_miniatura() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "stories_ig");

        syncService.runOnce();

        String storyExternalId = account.getExternalAccountId() + "-story1";
        Post story = postRepository
                .findByAccountIdAndExternalPostId(account.getId(), storyExternalId).orElseThrow();
        assertThat(story.isEphemeral()).isTrue();
        assertThat(story.getExpiresAt()).isNotNull();
        assertThat(story.getPostType()).isEqualTo(PostType.STORY);

        // Miniatura cacheada vía MediaService (track E).
        assertThat(mediaAssetRepository.findByPostId(story.getId())).isNotEmpty();

        // Métricas de story (nivel POST en el catálogo).
        LocalDate today = LocalDate.now(ASU);
        assertThat(postSnapshotRepository
                .findByPostIdAndMetricKeyAndCaptureDate(story.getId(), "ig_story_reach", today)).isPresent();
    }

    @Test
    void tiktok_captura_cuenta_y_videos_con_miniatura_y_sin_stories() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.TIKTOK, "tiktok_acct");

        syncService.runOnce();

        assertThat(countAccountSnapshots(account.getId())).isEqualTo(3);   // 3 métricas CORE de cuenta TT
        assertThat(postRepository.findByAccountId(account.getId())).hasSize(2); // 2 videos, ninguno efímero
        assertThat(postRepository.findByAccountId(account.getId()))
                .noneMatch(Post::isEphemeral);
        assertThat(countPostSnapshots(account.getId())).isEqualTo(8);      // 4 métricas * 2 videos
        // TAREA F: cada video cachea su miniatura (remote_thumbnail_url → storage); sin stories.
        assertThat(mediaAssetRepository.count()).isEqualTo(2);
    }

    @Test
    void cachea_miniatura_real_de_posts_normales_y_es_idempotente() {
        // TAREA F: el sync baja remote_thumbnail_url → media_assets para los posts del feed (no solo stories).
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "thumbs_ig");

        syncService.runOnce();

        // Los 2 posts normales del feed tienen miniatura cacheada con bytes reales.
        String p1 = account.getExternalAccountId() + "-p1";
        Post post1 = postRepository.findByAccountIdAndExternalPostId(account.getId(), p1).orElseThrow();
        assertThat(post1.isEphemeral()).isFalse();
        List<com.filgrama.domain.MediaAsset> p1Assets = mediaAssetRepository.findByPostId(post1.getId());
        assertThat(p1Assets).hasSize(1);
        assertThat(p1Assets.get(0).getContentType()).isEqualTo("image/jpeg");
        assertThat(p1Assets.get(0).getBytes()).isPositive();
        assertThat(p1Assets.get(0).getPurgeAfter()).isNull(); // post normal: no se purga como story

        // 2 posts normales + 1 story = 3 miniaturas en total.
        assertThat(mediaAssetRepository.count()).isEqualTo(3);

        // Re-run el mismo día NO re-descarga ni duplica miniaturas.
        mockProvider.setSeed(2_000L);
        syncService.runOnce();
        assertThat(mediaAssetRepository.findByPostId(post1.getId())).hasSize(1);
        assertThat(mediaAssetRepository.count()).isEqualTo(3);
    }

    // ---- TAREA A: sync por-cuenta (scan al conectar) ----

    @Test
    void syncAccountNow_sincroniza_una_sola_cuenta() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "scan_ig");

        Long runId = syncService.syncAccountNow(account);

        SyncRun run = syncRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(run.getAccountsTotal()).isEqualTo(1);
        assertThat(run.getAccountsOk()).isEqualTo(1);
        // Trajo posts + métricas + miniaturas al instante.
        assertThat(postRepository.findByAccountId(account.getId())).hasSize(3);
        assertThat(countAccountSnapshots(account.getId())).isEqualTo(5);
        assertThat(mediaAssetRepository.count()).isEqualTo(3);
    }

    @Test
    void syncAccountNow_bestEffort_no_relanza_si_la_cuenta_falla() {
        var client = newClient("America/Asuncion");
        SocialAccount bad = connectAccount(client.getId(), Platform.INSTAGRAM, "boom_ig"); // sentinela de fallo

        Long runId = syncService.syncAccountNow(bad); // NO debe lanzar

        SyncRun run = syncRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.PARTIAL);
        assertThat(run.getAccountsFailed()).isEqualTo(1);
        List<SyncAccountResult> results = syncAccountResultRepository.findByRunId(runId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(SyncAccountStatus.ERROR);
    }
}
