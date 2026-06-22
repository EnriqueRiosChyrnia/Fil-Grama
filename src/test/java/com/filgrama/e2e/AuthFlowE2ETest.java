package com.filgrama.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.auth.web.dto.LoginResponse;
import com.filgrama.auth.web.dto.RefreshResponse;
import com.filgrama.auth.web.dto.UserDto;

/**
 * Flujo de autenticación: login + {@code /me}, y la rotación de refresh con detección de reuso
 * (comportamiento de seguridad clave del track Auth). Cubre los casos 3 y 5 del track.
 */
class AuthFlowE2ETest extends AbstractE2ETest {

    @Test
    void login_emite_tokens_y_me_devuelve_el_usuario() {
        LoginResponse session = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.user().email()).isEqualTo(ADMIN_EMAIL);

        ResponseEntity<UserDto> me = get("/api/v1/auth/me", session.accessToken(), UserDto.class);

        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().email()).isEqualTo(ADMIN_EMAIL);
        assertThat(me.getBody().role()).isEqualTo("ADMIN");
    }

    @Test
    void refresh_rota_los_tokens_y_detecta_reuso_revocando_la_familia() {
        String oldRefresh = login(ADMIN_EMAIL, ADMIN_PASSWORD).refreshToken();

        // 1) Rotación válida: el refresh viejo entrega un par nuevo.
        ResponseEntity<RefreshResponse> rotated = post("/api/v1/auth/refresh",
                Map.of("refreshToken", oldRefresh), null, RefreshResponse.class);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);
        assertThat(rotated.getBody()).isNotNull();
        String newRefresh = rotated.getBody().refreshToken();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);

        // 2) Reuso del refresh viejo (ya rotado) => reuso detectado => 401 + familia revocada.
        ResponseEntity<Map> reuse = post("/api/v1/auth/refresh",
                Map.of("refreshToken", oldRefresh), null, Map.class);
        assertThat(reuse.getStatusCode().value()).isEqualTo(401);

        // 3) Al revocar la familia, el refresh NUEVO también queda inválido.
        ResponseEntity<Map> newAfterReuse = post("/api/v1/auth/refresh",
                Map.of("refreshToken", newRefresh), null, Map.class);
        assertThat(newAfterReuse.getStatusCode().value()).isEqualTo(401);
    }
}
