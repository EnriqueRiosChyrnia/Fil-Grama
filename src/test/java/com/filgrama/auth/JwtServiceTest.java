package com.filgrama.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.filgrama.domain.enums.Role;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-0123456789-abcdef";

    private JwtService service(Duration accessTtl) {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setAccessTtl(accessTtl);
        return new JwtService(props);
    }

    @Test
    void issuesAndParsesRoundTrip() {
        JwtService jwt = service(Duration.ofMinutes(15));
        String token = jwt.issueAccessToken(42L, Role.ADMIN);

        JwtService.ParsedToken parsed = jwt.parse(token);

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = service(Duration.ofSeconds(-1)); // exp en el pasado
        String token = jwt.issueAccessToken(1L, Role.EMPLEADO);

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = service(Duration.ofMinutes(15));
        String token = jwt.issueAccessToken(1L, Role.ADMIN);

        assertThatThrownBy(() -> jwt.parse(token + "tampered")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = service(Duration.ofMinutes(15)).issueAccessToken(7L, Role.ADMIN);

        JwtProperties other = new JwtProperties();
        other.setSecret("another-secret-another-secret-another-secret-0123456789-xyz");
        JwtService stranger = new JwtService(other);

        assertThatThrownBy(() -> stranger.parse(token)).isInstanceOf(JwtException.class);
    }
}
