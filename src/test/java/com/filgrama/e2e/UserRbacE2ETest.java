package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.user.dto.UserResponse;

/**
 * RBAC real (ADMIN vs EMPLEADO) y desactivación de usuario, ejercidos por HTTP contra la app
 * completa con {@code @EnableMethodSecurity} activo. Cubre los casos 4 y 6 del track.
 */
class UserRbacE2ETest extends AbstractE2ETest {

    private String uniqueEmail() {
        return "empleado-" + UUID.randomUUID() + "@filgrama.local";
    }

    private UserResponse createEmployee(String adminToken, String email, String password) {
        ResponseEntity<UserResponse> created = post("/api/v1/users",
                Map.of("email", email, "fullName", "Empleado E2E", "role", "EMPLEADO", "password", password),
                adminToken, UserResponse.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        return created.getBody();
    }

    @Test
    void empleado_no_puede_endpoint_admin_pero_si_clientes() {
        String admin = adminToken();
        String email = uniqueEmail();
        String password = "Empleado123!";
        UserResponse employee = createEmployee(admin, email, password);
        assertThat(employee.role().name()).isEqualTo("EMPLEADO");

        // El admin SÍ puede listar usuarios sin filtros (ejercita el list sin Specification).
        ResponseEntity<Map> adminList = get("/api/v1/users", admin, Map.class);
        assertThat(adminList.getStatusCode().value()).isEqualTo(200);

        String employeeToken = login(email, password).accessToken();

        // El empleado NO puede listar usuarios ([ADMIN] @PreAuthorize hasRole('ADMIN')).
        ResponseEntity<Map> adminOnly = get("/api/v1/users", employeeToken, Map.class);
        assertThat(adminOnly.getStatusCode().value()).isEqualTo(403);

        // El empleado SÍ puede listar clientes (cualquier usuario autenticado).
        ResponseEntity<Map> clients = get("/api/v1/clients", employeeToken, Map.class);
        assertThat(clients.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void usuario_desactivado_no_puede_loguear() {
        String admin = adminToken();
        String email = uniqueEmail();
        String password = "Empleado123!";
        UserResponse employee = createEmployee(admin, email, password);

        // Antes de desactivar, el login funciona.
        assertThat(login(email, password).accessToken()).isNotBlank();

        // Admin desactiva al usuario.
        ResponseEntity<UserResponse> patched = patch("/api/v1/users/" + employee.id(),
                Map.of("isActive", false), admin, UserResponse.class);
        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        assertThat(patched.getBody()).isNotNull();
        assertThat(patched.getBody().isActive()).isFalse();

        // Ya no puede loguear: 401.
        ResponseEntity<Map> denied = post("/api/v1/auth/login",
                Map.of("email", email, "password", password), null, Map.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(401);
    }
}
