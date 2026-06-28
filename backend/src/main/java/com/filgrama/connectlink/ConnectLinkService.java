package com.filgrama.connectlink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.connectlink.dto.AuthorizationUrlResponse;
import com.filgrama.connectlink.dto.ConnectLinkResponse;
import com.filgrama.connectlink.dto.ConnectLinkSummary;
import com.filgrama.connectlink.dto.PublicLinkInfo;
import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.oauth.OAuthProviderRegistry;
import com.filgrama.oauth.Platforms;
import com.filgrama.oauth.state.OAuthOrigin;
import com.filgrama.oauth.state.OAuthStateService;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Gestión del <b>link compartible de conexión</b> (onboarding self-service del cliente).
 *
 * <p>El link no guarda tokens: sólo habilita arrancar el OAuth acotado a un {@code clientId} (y opc.
 * a una red / cuenta a reconectar). El token raw (≥256 bits, url-safe) se genera con {@link SecureRandom}
 * y se devuelve <b>una sola vez</b> al crear; en DB vive sólo su {@code sha-256} (hex). Vencido/revocado →
 * los endpoints públicos responden {@code 410}; inexistente → {@code 404}. Multi-uso hasta expirar/revocar.
 *
 * <p>Spec: {@code spec/09-flujo-oauth.md} §"Link compartible", {@code spec/02} (tabla {@code connect_links}),
 * {@code spec/04} (CU9).
 */
@Service
public class ConnectLinkService {

    /** TTL por defecto del link. */
    private static final long TTL_HOURS = 72;

    private final ConnectLinkRepository linkRepo;
    private final ClientRepository clientRepo;
    private final SocialAccountRepository accountRepo;
    private final OAuthStateService stateService;
    private final OAuthProviderRegistry providers;
    private final String linkBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public ConnectLinkService(ConnectLinkRepository linkRepo,
                              ClientRepository clientRepo,
                              SocialAccountRepository accountRepo,
                              OAuthStateService stateService,
                              OAuthProviderRegistry providers,
                              @Value("${app.connect-link-base-url:http://localhost:5173/connect}") String linkBaseUrl) {
        this.linkRepo = linkRepo;
        this.clientRepo = clientRepo;
        this.accountRepo = accountRepo;
        this.stateService = stateService;
        this.providers = providers;
        this.linkBaseUrl = linkBaseUrl;
    }

    // ---- Agencia (autenticada) ----

    /**
     * Crea un connect-link para {@code clientId}. Si {@code accountId} viene, debe ser de ese cliente
     * (y de {@code platform} si también viene); cuando no se fija red, la hereda de la cuenta para que
     * el guard del open_id esperado quede coherente. Devuelve el token raw <b>sólo</b> en esta respuesta.
     */
    @Transactional
    public ConnectLinkResponse create(Long clientId, Platform platform, Long accountId, Long createdBy) {
        Client client = clientRepo.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("Client %d not found".formatted(clientId)));

        Platform resolvedPlatform = platform;
        if (accountId != null) {
            SocialAccount account = loadAccount(accountId);
            if (!account.getClientId().equals(client.getId())) {
                throw ApiException.badRequest(
                        "La cuenta %d no pertenece al cliente %d".formatted(accountId, clientId));
            }
            if (platform != null && account.getPlatform() != platform) {
                throw ApiException.badRequest(
                        "La cuenta %d no es de la red %s".formatted(accountId, platform.name()));
            }
            // Sin red explícita: la fija la de la cuenta a reconectar.
            resolvedPlatform = account.getPlatform();
        }

        String raw = generateToken();
        ConnectLink link = new ConnectLink();
        link.setClientId(clientId);
        link.setTokenHash(sha256Hex(raw));
        link.setPlatform(resolvedPlatform);
        link.setExpectedAccountId(accountId);
        link.setCreatedBy(createdBy);
        link.setExpiresAt(Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS));
        ConnectLink saved = linkRepo.save(link);

        String url = linkBaseUrl + "/" + raw;
        return new ConnectLinkResponse(raw, url, saved.getExpiresAt());
    }

    /** Links vigentes (no revocados ni vencidos) del cliente; sin token raw. */
    @Transactional(readOnly = true)
    public List<ConnectLinkSummary> listVigentes(Long clientId) {
        if (!clientRepo.existsById(clientId)) {
            throw ApiException.notFound("Client %d not found".formatted(clientId));
        }
        Instant now = Instant.now();
        return linkRepo.findByClientIdAndRevokedAtIsNull(clientId).stream()
                .filter(l -> l.getExpiresAt().isAfter(now))
                .map(ConnectLinkSummary::from)
                .toList();
    }

    /** Revoca (desactiva) un link por id. Idempotente si ya estaba revocado. */
    @Transactional
    public void revoke(Long id) {
        ConnectLink link = linkRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Connect-link %d not found".formatted(id)));
        if (link.getRevokedAt() == null) {
            link.setRevokedAt(Instant.now());
            linkRepo.save(link);
        }
    }

    /** Invalida los links pendientes atados a una cuenta (lo llama la baja de la cuenta). */
    @Transactional
    public void revokeByExpectedAccount(Long accountId) {
        Instant now = Instant.now();
        List<ConnectLink> pending = linkRepo.findByExpectedAccountIdAndRevokedAtIsNull(accountId);
        for (ConnectLink link : pending) {
            link.setRevokedAt(now);
        }
        linkRepo.saveAll(pending);
    }

    // ---- Público (sin login; acotado al cliente del token) ----

    /** Metadatos para la página pública de conexión. Inexistente → 404; vencido/revocado → 410. */
    @Transactional(readOnly = true)
    public PublicLinkInfo resolvePublic(String token) {
        ConnectLink link = requireUsable(token);
        Client client = clientRepo.findById(link.getClientId())
                .orElseThrow(() -> ApiException.notFound("Cliente del link no encontrado"));
        String platform = link.getPlatform() != null ? link.getPlatform().name() : null;
        // Checklist abierto del onboarding multi-cuenta: cuentas ya CONNECTED del cliente del token.
        // Mínimo (handle + red); nunca métricas ni credenciales. spec/09 §"Onboarding multi-cuenta".
        List<PublicLinkInfo.ConnectedAccount> connectedAccounts = accountRepo.findByClientId(link.getClientId()).stream()
                .filter(a -> a.getStatus() == AccountStatus.CONNECTED)
                .map(a -> new PublicLinkInfo.ConnectedAccount(a.getHandle(), a.getPlatform().name()))
                .toList();
        return new PublicLinkInfo(client.getName(), platform, link.getExpiresAt(), connectedAccounts);
    }

    /**
     * Arranca el OAuth público acotado al cliente del token: valida el link (existe/vigente/red
     * permitida), resuelve el {@code expectedExt} si es reconexión, emite el {@code state} firmado con
     * {@code origin=LINK} y {@code connected_by = created_by}, y devuelve la URL de autorización. Marca
     * {@code usedAt} (el link sigue siendo multi-uso).
     */
    @Transactional
    public AuthorizationUrlResponse startOauth(String token, String platformPath) {
        ConnectLink link = requireUsable(token);
        Platform platform = Platforms.fromPath(platformPath)
                .orElseThrow(() -> ApiException.badRequest("Plataforma inválida: " + platformPath));
        if (link.getPlatform() != null && link.getPlatform() != platform) {
            throw ApiException.badRequest("El link no habilita la red " + platformPath);
        }

        String expectedExt = null;
        if (link.getExpectedAccountId() != null) {
            expectedExt = loadAccount(link.getExpectedAccountId()).getExternalAccountId();
        }

        String state = stateService.issue(link.getClientId(), platform, link.getCreatedBy(),
                expectedExt, OAuthOrigin.LINK);
        String url = providers.forPlatform(platform).buildAuthorizationUrl(platform, state, true);

        link.setUsedAt(Instant.now());
        linkRepo.save(link);
        return new AuthorizationUrlResponse(url);
    }

    // ---- helpers ----

    private SocialAccount loadAccount(Long accountId) {
        return accountRepo.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account %d not found".formatted(accountId)));
    }

    /** Hashea el token entrante y busca por hash; valida vigencia. 404 si no existe, 410 si murió. */
    private ConnectLink requireUsable(String token) {
        ConnectLink link = linkRepo.findByTokenHash(sha256Hex(token))
                .orElseThrow(() -> ApiException.notFound("Link no encontrado"));
        if (link.getRevokedAt() != null || link.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "Gone", "El link expiró o fue revocado");
        }
        return link;
    }

    /** Token opaco de 256 bits, Base64 url-safe sin padding. Sólo se devuelve al crear; jamás se loguea. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e); // JDK siempre lo trae
        }
    }
}
