package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Red de seguridad de borde: endpoint público, deny-by-default y formato de error RFC 7807.
 * Cubre los casos 1, 2 y 10 del track.
 */
class HealthAndSecurityE2ETest extends AbstractE2ETest {

    @Test
    void health_es_publico_sin_token() {
        ResponseEntity<Map> res = get("/api/v1/health", null, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("status", "UP");
    }

    @Test
    void endpoint_protegido_sin_token_responde_401_problem_json() {
        ResponseEntity<Map> res = get("/api/v1/clients", null, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(res.getHeaders().getContentType())
                .isNotNull()
                .matches(ct -> ct.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(res.getBody()).containsEntry("status", 401);
        assertThat(res.getBody().get("title")).isNotNull();
    }

    @Test
    void recurso_inexistente_responde_404_rfc7807() {
        ResponseEntity<Map> res = get("/api/v1/clients/999999", adminToken(), Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getHeaders().getContentType())
                .isNotNull()
                .matches(ct -> ct.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(res.getBody()).containsEntry("status", 404);
        assertThat(res.getBody().get("title")).isNotNull();
        assertThat(res.getBody().get("detail")).isNotNull();
    }
}
