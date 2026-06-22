package com.filgrama.auth;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Fail-fast de secretos en perfil {@code prod}: si alguna clave sensible sigue usando el
 * default de DESARROLLO commiteado (o está vacía), <b>aborta el arranque</b> con un mensaje claro.
 * Mejor no arrancar que arrancar inseguro (firma JWT predecible, tokens OAuth descifrables).
 *
 * <p>Solo se carga con {@code @Profile("prod")}; en dev/test/local ni existe, así que la app
 * levanta con los defaults cómodos de siempre.
 *
 * <p>Valida presencia/no-default — NO mueve ni duplica la lógica de cifrado/firma (vive en los
 * tracks Auth/C). Lee los valores efectivos de la config:
 * <ul>
 *   <li>{@code security.jwt.secret} (vía {@link JwtProperties}).</li>
 *   <li>{@code security.token-encryption-key} (TokenCipher, track C).</li>
 *   <li>{@code oauth.state-secret} (OAuthStateService, track C).</li>
 * </ul>
 * Cada uno se sobreescribe por env en prod (ver README → "Variables sensibles para prod").
 */
@Component
@Profile("prod")
public class ProdSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(ProdSecretsValidator.class);

    /** Defaults DEV conocidos del JWT (JwtProperties / application.yml). */
    private static final Set<String> JWT_DEV_DEFAULTS = Set.of(
            "dev-only-insecure-jwt-secret-change-me-please-min-32-bytes-0123456789");

    /** Defaults DEV conocidos de la clave de cifrado de tokens (application.yml + fallback TokenCipher). */
    private static final Set<String> TOKEN_KEY_DEV_DEFAULTS = Set.of(
            "jCBVFCXsE7fpBhaXEW30ah5xcR5dy1YyNovheoobJus=",
            "MjciXevgsHmpyP3wf6vOZRS17GPYQGc7EJwFbeuW9YM=");

    /** Defaults DEV conocidos del secreto de state OAuth (application.yml + fallback OAuthStateService). */
    private static final Set<String> STATE_SECRET_DEV_DEFAULTS = Set.of(
            "RmXiLVwyWORp/QGW1EIskYiVE3++6aRjJu7r90wHsE8=",
            "wmKWIhsMq9NtiXJnItX8oVx4u01AQ6MkjccOO0OtD50=");

    private final String jwtSecret;
    private final String tokenEncryptionKey;
    private final String oauthStateSecret;

    public ProdSecretsValidator(
            JwtProperties jwtProperties,
            @Value("${security.token-encryption-key:}") String tokenEncryptionKey,
            @Value("${oauth.state-secret:}") String oauthStateSecret) {
        this.jwtSecret = jwtProperties.getSecret();
        this.tokenEncryptionKey = tokenEncryptionKey;
        this.oauthStateSecret = oauthStateSecret;
    }

    /**
     * Corre en el arranque (perfil prod). Lanza {@link IllegalStateException} —que Boot propaga
     * como fallo de creación de bean y termina el proceso— si algún secreto es default/vacío.
     */
    @PostConstruct
    public void validate() {
        requireProductionSecret("security.jwt.secret", "SECURITY_JWT_SECRET",
                jwtSecret, JWT_DEV_DEFAULTS);
        requireProductionSecret("security.token-encryption-key", "SECURITY_TOKEN_ENCRYPTION_KEY",
                tokenEncryptionKey, TOKEN_KEY_DEV_DEFAULTS);
        requireProductionSecret("oauth.state-secret", "OAUTH_STATE_SECRET",
                oauthStateSecret, STATE_SECRET_DEV_DEFAULTS);
        log.info("[prod] Validación de secretos OK: todas las claves sensibles fueron sobrescritas.");
    }

    /**
     * Falla si {@code value} está vacío o coincide con un default de desarrollo conocido.
     * El mensaje nombra la clave de config y la env var que hay que setear.
     */
    private static void requireProductionSecret(
            String propertyKey, String envVar, String value, Set<String> devDefaults) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "FATAL [prod]: '" + propertyKey + "' está vacío. "
                    + "Configurá la env var " + envVar + " con un secreto propio antes de arrancar.");
        }
        if (devDefaults.contains(value)) {
            throw new IllegalStateException(
                    "FATAL [prod]: '" + propertyKey + "' sigue usando el default INSEGURO de desarrollo. "
                    + "Configurá la env var " + envVar + " con un secreto propio (no commiteado) "
                    + "antes de arrancar en producción.");
        }
    }
}
