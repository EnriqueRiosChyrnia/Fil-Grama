package com.filgrama.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.domain.enums.Platform;

/**
 * Contrato + RBAC de los endpoints {@code /api/v1/sync} por HTTP contra la app completa
 * ({@code @EnableMethodSecurity} activo). Cubre la "definición de terminado": ADMIN → 202 + runId,
 * EMPLEADO → 403, detalle de la corrida, 401 sin token y 404 de corrida inexistente.
 */
class SyncControllerTest extends SyncTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void admin_dispara_corrida_y_consulta_historial_y_detalle() {
        var client = newClient("America/Asuncion");
        connectAccount(client.getId(), Platform.INSTAGRAM, "ctrl_ig");
        String admin = adminToken();

        ResponseEntity<Map> run = post("/api/v1/sync/run", null, admin, Map.class);
        assertThat(run.getStatusCode().value()).isEqualTo(202);
        assertThat(run.getBody()).containsKey("runId");
        long runId = ((Number) run.getBody().get("runId")).longValue();

        ResponseEntity<Map> detail = get("/api/v1/sync/runs/" + runId, admin, Map.class);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(detail.getBody()).containsKeys("run", "accounts");
        Map<String, Object> runNode = (Map<String, Object>) detail.getBody().get("run");
        assertThat(runNode.get("status")).isEqualTo("SUCCESS");
        assertThat((Iterable<?>) detail.getBody().get("accounts")).hasSize(1);

        ResponseEntity<Map> runs = get("/api/v1/sync/runs", admin, Map.class);
        assertThat(runs.getStatusCode().value()).isEqualTo(200);
        assertThat(runs.getBody()).containsKeys("content", "totalElements");
    }

    @Test
    void empleado_no_puede_disparar_corrida() {
        String admin = adminToken();
        String employee = employeeToken(admin);

        ResponseEntity<Map> run = post("/api/v1/sync/run", null, employee, Map.class);
        assertThat(run.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void sin_token_es_401() {
        ResponseEntity<Map> run = post("/api/v1/sync/run", null, null, Map.class);
        assertThat(run.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void corrida_inexistente_devuelve_404_problem_json() {
        String admin = adminToken();
        ResponseEntity<Map> res = get("/api/v1/sync/runs/999999", admin, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody()).containsKey("status");
        assertThat(res.getBody().get("status")).isEqualTo(404);
    }
}
