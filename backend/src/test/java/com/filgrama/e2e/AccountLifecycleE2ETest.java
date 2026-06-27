package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.filgrama.user.dto.UserResponse;

/**
 * Ciclo de vida de la cuenta por HTTP contra la app completa con {@code @EnableMethodSecurity}.
 * Verifica que <b>dar de baja</b> ({@code DELETE /accounts/{id}}) es sólo admin: el empleado recibe
 * {@code 403} (el gate corre ANTES del servicio) y el admin lo atraviesa (cuenta inexistente → 404).
 * El comportamiento de la baja en sí (revoca + borra credencial + {@code REMOVED} + revoca links) lo
 * cubren los tests de servicio. Cubre CU10 (parte de seguridad).
 */
class AccountLifecycleE2ETest extends AbstractE2ETest {

    @Test
    void darDeBaja_empleadoRecibe403_adminAtraviesaElGate() {
        String admin = adminToken();

        // Crea un empleado y obtené su token.
        String email = "empleado-" + UUID.randomUUID() + "@filgrama.local";
        String password = "Empleado123!";
        ResponseEntity<UserResponse> created = post("/api/v1/users",
                Map.of("email", email, "fullName", "Empleado E2E", "role", "EMPLEADO", "password", password),
                admin, UserResponse.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String employeeToken = login(email, password).accessToken();

        // Empleado → 403 (method-security corta antes de tocar el servicio; da igual que la cuenta exista).
        ResponseEntity<Map> empleado = exchange(HttpMethod.DELETE, "/api/v1/accounts/999999",
                null, employeeToken, Map.class);
        assertThat(empleado.getStatusCode().value()).isEqualTo(403);

        // Admin atraviesa el gate y llega al servicio: cuenta inexistente → 404 (no 403).
        ResponseEntity<Map> adminResp = exchange(HttpMethod.DELETE, "/api/v1/accounts/999999",
                null, admin, Map.class);
        assertThat(adminResp.getStatusCode().value()).isEqualTo(404);
    }
}
