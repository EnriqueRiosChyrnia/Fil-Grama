package com.filgrama.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.AudienceDemographicsCapture;
import com.filgrama.sync.capture.dto.DemographicSegment;
import com.filgrama.sync.derive.SnapshotDeriver;

/**
 * Migración + derive de la demografía v1.1 contra Postgres real (Testcontainers): la tabla
 * {@code audience_demographics} aplica limpio, el upsert es idempotente por día, el deriver persiste
 * los segmentos correctos, y los splits {@code follow_type} respetan el tier del catálogo (CORE se
 * persiste, EXTENDED no).
 */
class AudienceDemographicsIntegrationTest extends SyncTestSupport {

    private static final ZoneId ASU = ZoneId.of("America/Asuncion");

    @Autowired
    private SnapshotDeriver deriver;

    private long countDemographics(Long accountId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audience_demographics WHERE account_id = ?", Long.class, accountId);
    }

    private BigDecimal demographicValue(Long accountId, String type, String value, LocalDate day) {
        return jdbc.queryForObject("""
                SELECT value FROM audience_demographics
                WHERE account_id = ? AND breakdown_type = ? AND breakdown_value = ? AND capture_date = ?
                """, BigDecimal.class, accountId, type, value, day);
    }

    @Test
    void migracion_y_upsert_idempotente_de_audience_demographics() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "demo_ig");
        LocalDate today = LocalDate.now(ASU);
        Instant now = Instant.now();

        upsertRepository.upsertDemographic(client.getId(), account.getId(), "FOLLOWER", "AGE", "25-34",
                new BigDecimal("40"), now, today);
        upsertRepository.upsertDemographic(client.getId(), account.getId(), "FOLLOWER", "COUNTRY", "PY",
                new BigDecimal("120"), now, today);
        assertThat(countDemographics(account.getId())).isEqualTo(2);

        // Re-run del mismo día sobre el MISMO segmento → upsert (no duplica) y último valor gana.
        upsertRepository.upsertDemographic(client.getId(), account.getId(), "FOLLOWER", "AGE", "25-34",
                new BigDecimal("45"), now, today);
        assertThat(countDemographics(account.getId())).isEqualTo(2);
        assertThat(demographicValue(account.getId(), "AGE", "25-34", today)).isEqualByComparingTo("45");
    }

    @Test
    void derive_demografia_persiste_filas_correctas_y_segundo_run_upserta() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "demo_derive_ig");
        LocalDate today = LocalDate.now(ASU);

        // Fixture: segmentos derivados de un payload crudo de demografía (follower_demographics).
        AudienceDemographicsCapture cap = new AudienceDemographicsCapture("raw", "{}", List.of(
                new DemographicSegment("FOLLOWER", "AGE", "25-34", new BigDecimal("40")),
                new DemographicSegment("FOLLOWER", "GENDER", "F", new BigDecimal("80")),
                new DemographicSegment("FOLLOWER", "COUNTRY", "PY", new BigDecimal("120")),
                new DemographicSegment("FOLLOWER", "GENDER", "M", new BigDecimal("57")),
                new DemographicSegment("FOLLOWER", "GENDER", "U", null))); // sin valor → no se persiste

        int persisted = deriver.deriveDemographics(account, cap, Instant.now(), today);
        assertThat(persisted).isEqualTo(4);                 // el segmento sin valor se omite
        assertThat(countDemographics(account.getId())).isEqualTo(4);
        assertThat(demographicValue(account.getId(), "GENDER", "F", today)).isEqualByComparingTo("80");

        // Segundo run del mismo día con valores nuevos → upsert, NO duplica.
        AudienceDemographicsCapture rerun = new AudienceDemographicsCapture("raw", "{}", List.of(
                new DemographicSegment("FOLLOWER", "AGE", "25-34", new BigDecimal("42")),
                new DemographicSegment("FOLLOWER", "GENDER", "F", new BigDecimal("85")),
                new DemographicSegment("FOLLOWER", "COUNTRY", "PY", new BigDecimal("123")),
                new DemographicSegment("FOLLOWER", "GENDER", "M", new BigDecimal("59"))));
        deriver.deriveDemographics(account, rerun, Instant.now(), today);

        assertThat(countDemographics(account.getId())).isEqualTo(4); // sigue sin duplicar
        assertThat(demographicValue(account.getId(), "GENDER", "F", today)).isEqualByComparingTo("85");
    }

    @Test
    void extras_de_cuenta_respetan_el_tier_del_catalogo() {
        var client = newClient("America/Asuncion");
        SocialAccount account = connectAccount(client.getId(), Platform.INSTAGRAM, "extras_ig");
        LocalDate today = LocalDate.now(ASU);

        // ig_views_followers = CORE (se persiste); ig_reach_followers = EXTENDED (NO se persiste).
        AccountCapture extras = new AccountCapture("raw", "{}", java.util.Map.of(
                "ig_views_followers", new BigDecimal("3600"),
                "ig_views_non_followers", new BigDecimal("1500"),
                "ig_profile_views", new BigDecimal("363"),
                "ig_reach_followers", new BigDecimal("1200"),
                "ig_reach_non_followers", new BigDecimal("584")));

        deriver.deriveAccount(account, extras, Instant.now(), today);

        assertThat(accountSnapshotRepository
                .findByAccountIdAndMetricKeyAndCaptureDate(account.getId(), "ig_views_followers", today))
                .isPresent();
        assertThat(accountSnapshotRepository
                .findByAccountIdAndMetricKeyAndCaptureDate(account.getId(), "ig_profile_views", today))
                .isPresent();
        // EXTENDED: catalogada pero no capturada en v1.1.
        assertThat(accountSnapshotRepository
                .findByAccountIdAndMetricKeyAndCaptureDate(account.getId(), "ig_reach_followers", today))
                .isEmpty();
    }
}
