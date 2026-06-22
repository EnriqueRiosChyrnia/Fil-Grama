package com.filgrama.oauth.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.filgrama.domain.enums.Platform;

import io.jsonwebtoken.security.Keys;

class OAuthStateServiceTest {

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode("wmKWIhsMq9NtiXJnItX8oVx4u01AQ6MkjccOO0OtD50="));

    private final OAuthStateService service = new OAuthStateService(KEY, 600);

    @Test
    void issueThenConsumeReturnsClaims() {
        String state = service.issue(42L, Platform.TIKTOK, 7L);
        OAuthState parsed = service.consume(state);

        assertThat(parsed.clientId()).isEqualTo(42L);
        assertThat(parsed.platform()).isEqualTo(Platform.TIKTOK);
        assertThat(parsed.userId()).isEqualTo(7L);
        assertThat(parsed.nonce()).isNotBlank();
    }

    @Test
    void reusedStateRejected() {
        String state = service.issue(1L, Platform.INSTAGRAM, 1L);
        service.consume(state); // primer uso OK
        assertThatThrownBy(() -> service.consume(state))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void tamperedSignatureRejected() {
        String state = service.issue(1L, Platform.FACEBOOK, 1L);
        assertThatThrownBy(() -> service.consume(state + "tampered"))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void expiredStateRejected() {
        OAuthStateService expired = new OAuthStateService(KEY, -10); // exp en el pasado
        String state = expired.issue(1L, Platform.TIKTOK, 1L);
        assertThatThrownBy(() -> expired.consume(state))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void nullUserIdAllowed() {
        String state = service.issue(5L, Platform.TIKTOK, null);
        assertThat(service.consume(state).userId()).isNull();
    }
}
