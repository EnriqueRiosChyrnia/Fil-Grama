package com.filgrama.e2e;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Callbacks de compliance de Meta por HTTP contra la app completa: valida el routing público (sin auth),
 * el mapeo a {@code 400} del {@code signed_request} inválido y el contrato exacto del Data Deletion
 * request ({@code {url, confirmation_code}}). Levanta el stack real (Flyway V7/V8 incluidas).
 */
class MetaComplianceE2ETest extends AbstractE2ETest {

    private static final String META_SECRET = "e2e-meta-app-secret";

    @DynamicPropertySource
    static void metaProperties(DynamicPropertyRegistry registry) {
        registry.add("oauth.meta.app-secret", () -> META_SECRET);
    }

    @Test
    void deauthorizeSinSignedRequestDevuelve400() {
        ResponseEntity<Map> res = exchange(HttpMethod.POST, "/api/v1/meta/deauthorize", null, null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void dataDeletionSinSignedRequestDevuelve400() {
        ResponseEntity<Map> res = exchange(HttpMethod.POST, "/api/v1/meta/data-deletion", null, null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void deauthorizeFirmaInvalidaDevuelve400() {
        String tampered = signedRequest("otro-secret", "{\"user_id\":\"MU-x\"}");
        ResponseEntity<Map> res = exchange(HttpMethod.POST,
                "/api/v1/meta/deauthorize?signed_request=" + tampered, null, null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dataDeletionFirmaValidaDevuelveUrlYConfirmationCode() {
        // Usuario sin cuentas: el borrado no afecta filas pero igual emite el confirmation_code.
        String signed = signedRequest(META_SECRET, "{\"user_id\":\"MU-sin-cuentas\"}");
        ResponseEntity<Map> res = exchange(HttpMethod.POST,
                "/api/v1/meta/data-deletion?signed_request=" + signed, null, null, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        // Formato exacto que exige Meta: claves "url" y "confirmation_code".
        assertThat(body).containsKey("url").containsKey("confirmation_code");
        assertThat((String) body.get("confirmation_code")).isNotBlank();
        assertThat((String) body.get("url"))
                .contains("/data-deletion")
                .contains("code=" + body.get("confirmation_code"));
    }

    private static String signedRequest(String secret, String payloadJson) {
        String payloadB64 = b64url(payloadJson.getBytes(UTF_8));
        return b64url(hmac(secret, payloadB64)) + "." + payloadB64;
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
