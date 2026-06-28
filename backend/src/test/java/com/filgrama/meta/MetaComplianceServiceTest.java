package com.filgrama.meta;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.DataDeletionRequest;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.meta.dto.DataDeletionResponse;
import com.filgrama.oauth.OAuthProviderRegistry;
import com.filgrama.oauth.config.OAuthProperties;
import com.filgrama.oauth.crypto.TokenCipher;
import com.filgrama.oauth.provider.MockOAuthProvider;
import com.filgrama.repository.AccountCredentialRepository;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.DataDeletionRequestRepository;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.RawApiPayloadRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Compliance Meta a nivel servicio: validación del {@code signed_request} (firma válida/ inválida/
 * payload corrupto), deauthorize (marca ERROR sólo las cuentas del usuario) y data-deletion (REMOVED +
 * purga + contrato {@code {url, confirmation_code}}). Repos mockeados (sin DB), {@code TokenCipher} real.
 */
@ExtendWith(MockitoExtension.class)
class MetaComplianceServiceTest {

    private static final String APP_SECRET = "meta-app-secret-de-prueba";
    private static final String STATUS_URL = "http://front.local/data-deletion";

    @Mock SocialAccountRepository accountRepo;
    @Mock AccountCredentialRepository credentialRepo;
    @Mock DataDeletionRequestRepository deletionRepo;
    @Mock PostRepository postRepo;
    @Mock PostMetricSnapshotRepository postSnapshotRepo;
    @Mock AccountMetricSnapshotRepository accountSnapshotRepo;
    @Mock MediaAssetRepository mediaRepo;
    @Mock RawApiPayloadRepository rawPayloadRepo;

    private final TokenCipher cipher = new TokenCipher("MjciXevgsHmpyP3wf6vOZRS17GPYQGc7EJwFbeuW9YM=");
    private OAuthProperties props;
    private MetaComplianceService service;

    @BeforeEach
    void setup() {
        props = new OAuthProperties();
        props.getMeta().setAppSecret(APP_SECRET);
        OAuthProviderRegistry providers = new OAuthProviderRegistry(List.of(new MockOAuthProvider()));
        service = new MetaComplianceService(accountRepo, credentialRepo, providers, cipher, props,
                deletionRepo, postRepo, postSnapshotRepo, accountSnapshotRepo, mediaRepo, rawPayloadRepo,
                STATUS_URL);
    }

    // ---- signed_request ----

    @Test
    void deauthorizeFirmaValidaMarcaErrorSoloLasCuentasDelUsuario() {
        SocialAccount conectada = account(1L, Platform.INSTAGRAM, AccountStatus.CONNECTED);
        SocialAccount yaError = account(2L, Platform.FACEBOOK, AccountStatus.ERROR);
        when(accountRepo.findByMetaUserId("MU-1")).thenReturn(List.of(conectada, yaError));

        service.deauthorize(signedRequest(APP_SECRET, "{\"user_id\":\"MU-1\",\"algorithm\":\"HMAC-SHA256\"}"));

        assertThat(conectada.getStatus()).isEqualTo(AccountStatus.ERROR);
        verify(accountRepo).save(conectada);
        // La que ya estaba ERROR no se re-guarda; otros usuarios no se tocan (findByMetaUserId acota).
        verify(accountRepo, never()).save(yaError);
    }

    @Test
    void firmaInvalidaDevuelve400YNoTocaNada() {
        // Firmado con OTRO secret → la firma no valida.
        String tampered = signedRequest("secret-equivocado", "{\"user_id\":\"MU-1\"}");

        assertThatThrownBy(() -> service.deauthorize(tampered))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(accountRepo, never()).findByMetaUserId(any());
        verify(accountRepo, never()).save(any());
    }

    @Test
    void payloadCorruptoDevuelve400() {
        // Firma válida sobre un payload que NO es JSON.
        String corrupt = signedRequest(APP_SECRET, "{ esto no es json");

        assertThatThrownBy(() -> service.deauthorize(corrupt))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void formatoSinPuntoDevuelve400() {
        assertThatThrownBy(() -> service.deauthorize("solo-una-parte"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sinUserIdDevuelve400() {
        String noUser = signedRequest(APP_SECRET, "{\"algorithm\":\"HMAC-SHA256\"}");

        assertThatThrownBy(() -> service.deauthorize(noUser))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- data-deletion ----

    @Test
    void dataDeletionBorraPurgaYDevuelveContrato() {
        SocialAccount acc = account(100L, Platform.INSTAGRAM, AccountStatus.CONNECTED);
        when(accountRepo.findByMetaUserId("MU-2")).thenReturn(List.of(acc));
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountCredential cred = new AccountCredential();
        cred.setAccountId(100L);
        cred.setAccessTokenEnc(cipher.encrypt("acc-tok"));
        when(credentialRepo.findById(100L)).thenReturn(java.util.Optional.of(cred));

        ArgumentCaptor<DataDeletionRequest> reqCaptor = ArgumentCaptor.forClass(DataDeletionRequest.class);
        when(deletionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DataDeletionResponse resp = service.requestDataDeletion(
                signedRequest(APP_SECRET, "{\"user_id\":\"MU-2\"}"));

        // Contrato exacto que pide Meta.
        assertThat(resp.confirmationCode()).isNotBlank();
        assertThat(resp.url()).startsWith(STATUS_URL).contains("code=" + resp.confirmationCode());

        // Borrado real: credencial + purga de hijos en orden FK + cuenta REMOVED.
        verify(credentialRepo).deleteById(100L);
        verify(mediaRepo).deleteByAccountId(100L);
        verify(postSnapshotRepo).deleteByAccountId(100L);
        verify(rawPayloadRepo).deleteByAccountId(100L);
        verify(accountSnapshotRepo).deleteByAccountId(100L);
        verify(postRepo).deleteByAccountId(100L);
        assertThat(acc.getStatus()).isEqualTo(AccountStatus.REMOVED);
        verify(accountRepo).save(acc);

        // Registro persistido para que el usuario consulte por código.
        verify(deletionRepo).save(reqCaptor.capture());
        DataDeletionRequest saved = reqCaptor.getValue();
        assertThat(saved.getMetaUserId()).isEqualTo("MU-2");
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getAccountsRemoved()).isEqualTo(1);
        assertThat(saved.getConfirmationCode()).isEqualTo(resp.confirmationCode());
    }

    // ---- helpers ----

    private static SocialAccount account(Long id, Platform platform, AccountStatus status) {
        SocialAccount a = new SocialAccount();
        a.setId(id);
        a.setPlatform(platform);
        a.setStatus(status);
        return a;
    }

    /** Arma un {@code signed_request} de Meta: {@code base64url(sig).base64url(payload)}, sin padding. */
    private static String signedRequest(String secret, String payloadJson) {
        String payloadB64 = b64url(payloadJson.getBytes(UTF_8));
        byte[] sig = hmac(secret, payloadB64);
        return b64url(sig) + "." + payloadB64;
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
