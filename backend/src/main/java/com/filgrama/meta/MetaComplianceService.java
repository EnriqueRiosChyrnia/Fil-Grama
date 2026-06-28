package com.filgrama.meta;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.filgrama.domain.DataDeletionRequest;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.error.ApiException;
import com.filgrama.meta.dto.DataDeletionResponse;
import com.filgrama.oauth.OAuthProviderRegistry;
import com.filgrama.oauth.config.OAuthProperties;
import com.filgrama.oauth.crypto.TokenCipher;
import com.filgrama.repository.AccountCredentialRepository;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.DataDeletionRequestRepository;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.RawApiPayloadRepository;
import com.filgrama.repository.SocialAccountRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compliance de Meta para App Review (spec/09 §Meta · §Ciclo de vida):
 * <ul>
 *   <li><b>Deauthorize callback</b>: el usuario quitó la app → su token murió → las cuentas Meta de ese
 *       usuario pasan a {@code ERROR} (se conserva la historia para reportes pasados).</li>
 *   <li><b>Data Deletion request</b>: borrado real de los datos de ese usuario — cuentas Meta a
 *       {@code REMOVED}, credenciales borradas y posts/snapshots purgados — devolviendo
 *       {@code {url, confirmation_code}} en el formato que exige Meta.</li>
 * </ul>
 *
 * <p>Ambos callbacks llegan <b>sin auth</b> (Meta no tiene sesión): la autenticidad la da el
 * {@code signed_request} firmado con el {@code app_secret} (HMAC-SHA256, comparación time-safe). Una
 * firma/payload inválido ⇒ {@code 400} y no se toca nada. El {@code app_secret} nunca se loguea.
 */
@Service
public class MetaComplianceService {

    private static final Logger log = LoggerFactory.getLogger(MetaComplianceService.class);
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final SecureRandom RNG = new SecureRandom();

    private final SocialAccountRepository accountRepo;
    private final AccountCredentialRepository credentialRepo;
    private final OAuthProviderRegistry providers;
    private final TokenCipher cipher;
    private final OAuthProperties props;
    private final DataDeletionRequestRepository deletionRepo;
    private final PostRepository postRepo;
    private final PostMetricSnapshotRepository postSnapshotRepo;
    private final AccountMetricSnapshotRepository accountSnapshotRepo;
    private final MediaAssetRepository mediaRepo;
    private final RawApiPayloadRepository rawPayloadRepo;
    /** Página pública del front que muestra el estado del borrado por código (formato exigido por Meta). */
    private final String statusUrl;

    public MetaComplianceService(SocialAccountRepository accountRepo,
                                 AccountCredentialRepository credentialRepo,
                                 OAuthProviderRegistry providers,
                                 TokenCipher cipher,
                                 OAuthProperties props,
                                 DataDeletionRequestRepository deletionRepo,
                                 PostRepository postRepo,
                                 PostMetricSnapshotRepository postSnapshotRepo,
                                 AccountMetricSnapshotRepository accountSnapshotRepo,
                                 MediaAssetRepository mediaRepo,
                                 RawApiPayloadRepository rawPayloadRepo,
                                 @Value("${app.data-deletion-status-url:http://localhost:5173/data-deletion}")
                                 String statusUrl) {
        this.accountRepo = accountRepo;
        this.credentialRepo = credentialRepo;
        this.providers = providers;
        this.cipher = cipher;
        this.props = props;
        this.deletionRepo = deletionRepo;
        this.postRepo = postRepo;
        this.postSnapshotRepo = postSnapshotRepo;
        this.accountSnapshotRepo = accountSnapshotRepo;
        this.mediaRepo = mediaRepo;
        this.rawPayloadRepo = rawPayloadRepo;
        this.statusUrl = statusUrl;
    }

    /**
     * Deauthorize callback: valida el {@code signed_request}, saca el {@code user_id} y marca sus cuentas
     * Meta como {@code ERROR} (token muerto), sin borrar historia y sin tocar las de otros usuarios.
     */
    @Transactional
    public void deauthorize(String signedRequest) {
        String userId = verifyAndExtractUserId(signedRequest);
        List<SocialAccount> accounts = accountRepo.findByMetaUserId(userId);
        for (SocialAccount account : accounts) {
            if (account.getStatus() != AccountStatus.ERROR) {
                account.setStatus(AccountStatus.ERROR);
                accountRepo.save(account);
            }
        }
        log.info("Meta deauthorize: {} cuenta(s) marcadas ERROR para el usuario Meta", accounts.size());
    }

    /**
     * Data Deletion request: valida el {@code signed_request}, saca el {@code user_id}, borra de verdad
     * los datos de ese usuario (cuentas a {@code REMOVED}, credenciales borradas, posts/snapshots/payloads
     * purgados) y persiste un {@code confirmation_code} único. Devuelve {@code {url, confirmation_code}}.
     */
    @Transactional
    public DataDeletionResponse requestDataDeletion(String signedRequest) {
        String userId = verifyAndExtractUserId(signedRequest);
        List<SocialAccount> accounts = accountRepo.findByMetaUserId(userId);
        for (SocialAccount account : accounts) {
            purgeAccount(account);
        }

        String code = newConfirmationCode();
        DataDeletionRequest request = new DataDeletionRequest();
        request.setConfirmationCode(code);
        request.setMetaUserId(userId);
        request.setStatus("COMPLETED");
        request.setAccountsRemoved(accounts.size());
        request.setCompletedAt(Instant.now());
        deletionRepo.save(request);

        log.info("Meta data-deletion: {} cuenta(s) purgadas, confirmation_code emitido", accounts.size());
        String url = UriComponentsBuilder.fromUriString(statusUrl)
                .queryParam("code", code)
                .encode().toUriString();
        return new DataDeletionResponse(url, code);
    }

    /** Borra de verdad los datos de una cuenta Meta: revoca, purga hijos en orden FK y la deja REMOVED. */
    private void purgeAccount(SocialAccount account) {
        Long accountId = account.getId();
        credentialRepo.findById(accountId).ifPresent(cred -> {
            try {
                String access = cipher.decrypt(cred.getAccessTokenEnc());
                String refresh = cred.getRefreshTokenEnc() != null
                        ? cipher.decrypt(cred.getRefreshTokenEnc()) : null;
                providers.forPlatform(account.getPlatform())
                        .revokeToken(account.getPlatform(), access, refresh);
            } catch (RuntimeException e) {
                // Best-effort: si la revocación remota falla, igual se borra la credencial local.
            }
            credentialRepo.deleteById(accountId);
        });

        // Orden FK: primero los hijos de posts, después los snapshots de cuenta, al final los posts.
        mediaRepo.deleteByAccountId(accountId);
        postSnapshotRepo.deleteByAccountId(accountId);
        rawPayloadRepo.deleteByAccountId(accountId);
        accountSnapshotRepo.deleteByAccountId(accountId);
        postRepo.deleteByAccountId(accountId);

        account.setStatus(AccountStatus.REMOVED);
        accountRepo.save(account);
    }

    // ---- signed_request (Meta) ----

    /**
     * Valida el {@code signed_request} de Meta ({@code <sig_base64url>.<payload_base64url>}) y devuelve el
     * {@code user_id} del payload. {@code sig} debe ser {@code HMAC-SHA256(payload_base64url, app_secret)}
     * comparado time-safe. Cualquier desvío (formato, base64, firma, payload, falta de {@code user_id})
     * ⇒ {@link ApiException} 400. El {@code app_secret} nunca se loguea.
     */
    private String verifyAndExtractUserId(String signedRequest) {
        if (signedRequest == null || signedRequest.isBlank()) {
            throw ApiException.badRequest("signed_request ausente");
        }
        String[] parts = signedRequest.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw ApiException.badRequest("signed_request con formato inválido");
        }
        byte[] expectedSig;
        byte[] payloadBytes;
        try {
            expectedSig = Base64.getUrlDecoder().decode(parts[0]);
            payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("signed_request no es base64url válido");
        }

        byte[] computed = hmacSha256(parts[1]);
        if (!MessageDigest.isEqual(expectedSig, computed)) {
            throw ApiException.badRequest("firma del signed_request inválida");
        }

        JsonNode payload;
        try {
            payload = JSON.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("payload del signed_request ilegible");
        }
        JsonNode userId = payload.path("user_id");
        if (userId.isMissingNode() || userId.isNull() || userId.asText().isBlank()) {
            throw ApiException.badRequest("signed_request sin user_id");
        }
        return userId.asText();
    }

    /** {@code HMAC-SHA256(message, app_secret)}; secret vacío (mal configurado) ⇒ no se puede verificar. */
    private byte[] hmacSha256(String message) {
        String secret = props.getMeta().getAppSecret();
        if (secret == null || secret.isEmpty()) {
            throw ApiException.badRequest("Meta app_secret no configurado: no se puede verificar el signed_request");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    /** Código de confirmación de alta entropía (16 bytes → base64url sin padding). */
    private static String newConfirmationCode() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
