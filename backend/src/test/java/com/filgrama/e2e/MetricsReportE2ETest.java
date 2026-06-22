package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * E2e de ruteo de los custom methods {@code :report}/{@code :batchReport} contra la app COMPLETA
 * (PathPatternParser + seguridad JWT + Jackson reales). No depende de snapshots seedeados: ejercita
 * los caminos de error/validación, que bastan para probar que las rutas con {@code :} resuelven y
 * llegan al service. El happy-path con datos vive en los tests unitarios del service.
 */
class MetricsReportE2ETest extends AbstractE2ETest {

    @Test
    void accountReportRouteResolvesAndReturns404ForMissingAccount() {
        // métrica válida (ig_reach está seedeada) → pasa validación y llega al lookup de cuenta → 404
        ResponseEntity<Map> res = post("/api/v1/accounts/999999/metrics:report",
                Map.of("metrics", List.of("ig_reach")), adminToken(), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void accountReportInvalidMetricIsBadRequest() {
        ResponseEntity<Map> res = post("/api/v1/accounts/999999/metrics:report",
                Map.of("metrics", List.of("bogus_metric")), adminToken(), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void postReportRouteResolvesAndReturns404ForMissingPost() {
        ResponseEntity<Map> res = post("/api/v1/posts/999999/metrics:report",
                Map.of("metrics", List.of("ig_reach")), adminToken(), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void batchReportRouteResolvesAndRejectsOverLimit() {
        List<Map<String, Object>> requests = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> Map.<String, Object>of("accountId", i, "metrics", List.of("ig_reach")))
                .toList();
        ResponseEntity<Map> res = post("/api/v1/metrics:batchReport",
                Map.of("requests", requests), adminToken(), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void reportRequiresAuth() {
        ResponseEntity<Map> res = post("/api/v1/accounts/1/metrics:report",
                Map.of("metrics", List.of("ig_reach")), null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    /** /v3/api-docs (fuente del codegen orval del front) debe exponer los nuevos paths y schemas. */
    @Test
    void openApiDocsExposeReportEndpointsAndSchemas() {
        ResponseEntity<String> docs = get("/v3/api-docs", null, String.class);
        assertThat(docs.getStatusCode().value()).isEqualTo(200);
        assertThat(docs.getBody())
                .contains("/api/v1/accounts/{id}/metrics:report")
                .contains("/api/v1/metrics:batchReport")
                .contains("/api/v1/posts/{id}/metrics:report")
                .contains("MetricsReportRequest")
                .contains("BatchReportRequest")
                .contains("AccountReportResponse")
                .contains("BatchReportResponse")
                .contains("MetricSeries")
                .contains("SeriesPoint");
    }
}
