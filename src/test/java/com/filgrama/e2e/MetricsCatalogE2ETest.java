package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.metrics.dto.MetricCatalogItem;

/**
 * Confirma que Flyway aplicó las migraciones contra el Postgres efímero: el catálogo expone las
 * 30 métricas CORE seedeadas por V3 (lo que prueba V1 + V2 + V3). Caso 7 del track.
 */
class MetricsCatalogE2ETest extends AbstractE2ETest {

    @Test
    void catalogo_expone_las_30_metricas_core_seedeadas() {
        ResponseEntity<MetricCatalogItem[]> res =
                get("/api/v1/metrics", adminToken(), MetricCatalogItem[].class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull().hasSize(30);
    }
}
