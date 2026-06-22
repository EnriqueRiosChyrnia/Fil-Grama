package com.filgrama.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Fail-fast de secretos en perfil prod. Los valores DEV son los defaults commiteados en
 * {@code application.yml} / las clases de cifrado; un secreto "real" es cualquier otro string.
 */
class ProdSecretsValidatorTest {

    // Defaults DEV reales (deben coincidir con application.yml / JwtProperties / TokenCipher / OAuthStateService).
    private static final String DEV_JWT = "dev-only-insecure-jwt-secret-change-me-please-min-32-bytes-0123456789";
    private static final String DEV_TOKEN_KEY = "jCBVFCXsE7fpBhaXEW30ah5xcR5dy1YyNovheoobJus=";
    private static final String DEV_STATE = "RmXiLVwyWORp/QGW1EIskYiVE3++6aRjJu7r90wHsE8=";

    private static final String REAL_JWT = "prod-rotated-jwt-secret-1234567890-abcdefghijklmnop-qrstuvwxyz";
    private static final String REAL_TOKEN_KEY = "AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHHIIIIJJJJKKK=";
    private static final String REAL_STATE = "ZZZZYYYYXXXXWWWWVVVVUUUUTTTTSSSSRRRRQQQQPPP=";

    private static ProdSecretsValidator validator(String jwt, String tokenKey, String state) {
        JwtProperties props = new JwtProperties();
        props.setSecret(jwt);
        return new ProdSecretsValidator(props, tokenKey, state);
    }

    @Test
    void abortsWhenJwtSecretIsDevDefault() {
        assertThatThrownBy(() -> validator(DEV_JWT, REAL_TOKEN_KEY, REAL_STATE).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.jwt.secret")
                .hasMessageContaining("SECURITY_JWT_SECRET");
    }

    @Test
    void abortsWhenTokenEncryptionKeyIsDevDefault() {
        assertThatThrownBy(() -> validator(REAL_JWT, DEV_TOKEN_KEY, REAL_STATE).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.token-encryption-key");
    }

    @Test
    void abortsWhenStateSecretIsDevDefault() {
        assertThatThrownBy(() -> validator(REAL_JWT, REAL_TOKEN_KEY, DEV_STATE).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oauth.state-secret");
    }

    @Test
    void abortsWhenSecretIsBlank() {
        assertThatThrownBy(() -> validator(REAL_JWT, "", REAL_STATE).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void passesWhenAllSecretsOverridden() {
        assertThatCode(() -> validator(REAL_JWT, REAL_TOKEN_KEY, REAL_STATE).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void devDefaultsMatchTheRealConfiguredDefaults() {
        // Si alguien rota el default DEV en application.yml/JwtProperties sin actualizar el validador,
        // el fail-fast dejaría de cubrir ese valor. Este test es el recordatorio.
        assertThat(new JwtProperties().getSecret()).isEqualTo(DEV_JWT);
    }
}
