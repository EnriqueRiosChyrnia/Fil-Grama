package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.client.dto.ClientDetailResponse;
import com.filgrama.client.dto.ClientResponse;
import com.filgrama.metrics.dto.SummaryResponse;

/**
 * Ciclo de vida vertical de un cliente (crear → listar → archivar) y humo multi-tenant: el resumen
 * de un cliente nunca mezcla datos de otro. Cubre los casos 8 y 9 del track.
 */
class ClientLifecycleE2ETest extends AbstractE2ETest {

    private ClientResponse createClient(String adminToken, String name) {
        ResponseEntity<ClientResponse> created = post("/api/v1/clients",
                Map.of("name", name), adminToken, ClientResponse.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        return created.getBody();
    }

    @Test
    void crud_vertical_crear_listar_archivar() {
        String admin = adminToken();
        // Nombre sin espacios: el valor va directo en el query string (q = ILIKE %name%) sin
        // tener que encodear — RestClient.uri() re-encodearía un '%' a '%25'.
        String name = "E2E-Cliente-" + UUID.randomUUID();

        ClientResponse created = createClient(admin, name);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);
        assertThat(created.status().name()).isEqualTo("ACTIVE");

        // Listado paginado, filtrado por nombre: el cliente recién creado aparece.
        ResponseEntity<Map> page = get("/api/v1/clients?q=" + name + "&size=50",
                admin, Map.class);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).anySatisfy(c ->
                assertThat(((Number) c.get("id")).longValue()).isEqualTo(created.id()));

        // Archivar → 204, y el detalle pasa a ARCHIVED.
        ResponseEntity<Void> archived = post("/api/v1/clients/" + created.id() + "/archive",
                null, admin, Void.class);
        assertThat(archived.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<ClientDetailResponse> detail =
                get("/api/v1/clients/" + created.id(), admin, ClientDetailResponse.class);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(detail.getBody()).isNotNull();
        assertThat(detail.getBody().status().name()).isEqualTo("ARCHIVED");
    }

    @Test
    void multi_tenant_humo_el_resumen_no_cruza_datos_entre_clientes() {
        String admin = adminToken();

        ClientResponse a = createClient(admin, "Tenant A " + UUID.randomUUID());
        ClientResponse b = createClient(admin, "Tenant B " + UUID.randomUUID());

        // El resumen de A está acotado a A (sin redes conectadas → sin datos), nunca a B.
        ResponseEntity<SummaryResponse> summaryA =
                get("/api/v1/clients/" + a.id() + "/summary", admin, SummaryResponse.class);
        assertThat(summaryA.getStatusCode().value()).isEqualTo(200);
        assertThat(summaryA.getBody()).isNotNull();
        assertThat(summaryA.getBody().clientId()).isEqualTo(a.id());
        assertThat(summaryA.getBody().clientId()).isNotEqualTo(b.id());
        assertThat(summaryA.getBody().platforms() == null || summaryA.getBody().platforms().isEmpty())
                .as("cliente sin redes conectadas no debe traer plataformas con datos")
                .isTrue();

        // Un client_id inexistente responde 404, nunca datos de otro tenant.
        ResponseEntity<Map> foreign =
                get("/api/v1/clients/99999999/summary", admin, Map.class);
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
    }
}
