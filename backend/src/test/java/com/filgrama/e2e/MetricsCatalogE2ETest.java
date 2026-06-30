package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.metrics.dto.MetricCatalogItem;

/**
 * Confirma que Flyway aplicó las migraciones contra el Postgres efímero: el catálogo expone las
 * métricas CORE/ACTIVE seedeadas por V3 (30) + las adiciones CORE de la v1.1/V10 (11) = 41 (lo que
 * prueba V1 + V2 + V3 + … + V10). Las dos {@code ig_reach_*} de V10 son EXTENDED → no aparecen.
 */
class MetricsCatalogE2ETest extends AbstractE2ETest {

    @Test
    void catalogo_expone_las_metricas_core_seedeadas_incluida_la_v11() {
        ResponseEntity<MetricCatalogItem[]> res =
                get("/api/v1/metrics", adminToken(), MetricCatalogItem[].class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull().hasSize(41);

        List<String> keys = Arrays.stream(res.getBody()).map(MetricCatalogItem::key).toList();
        // Adiciones v1.1 (FG-T1) promovidas/agregadas a CORE.
        assertThat(keys).contains("ig_follower_demographics", "ig_profile_views", "ig_views_followers",
                "ig_taps_whatsapp", "ig_post_reposts", "ig_reels_avg_watch_time");
        // Las EXTENDED quedan catalogadas pero fuera del listado CORE.
        assertThat(keys).doesNotContain("ig_reach_followers", "ig_reach_non_followers");
    }
}
