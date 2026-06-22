package com.filgrama.oauth.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private final TokenCipher cipher = new TokenCipher("MjciXevgsHmpyP3wf6vOZRS17GPYQGc7EJwFbeuW9YM=");

    @Test
    void roundTripRecoversPlaintext() {
        byte[] enc = cipher.encrypt("super-secret-token");
        assertThat(cipher.decrypt(enc)).isEqualTo("super-secret-token");
    }

    @Test
    void ciphertextDoesNotLeakPlaintext() {
        byte[] enc = cipher.encrypt("super-secret-token");
        assertThat(new String(enc, StandardCharsets.ISO_8859_1)).doesNotContain("super-secret-token");
    }

    @Test
    void freshIvPerEncryption() {
        // Mismo texto, dos cifrados distintos (IV aleatorio por llamada).
        assertThat(cipher.encrypt("x")).isNotEqualTo(cipher.encrypt("x"));
    }
}
