package com.filgrama.account.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.filgrama.account.dto.AccountResponse;
import com.filgrama.account.dto.ConnectResponse;
import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.oauth.OAuthException;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthProfile;
import com.filgrama.oauth.OAuthProviderRegistry;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.Platforms;
import com.filgrama.oauth.TokenRevokedException;
import com.filgrama.oauth.config.OAuthProperties;
import com.filgrama.oauth.crypto.TokenCipher;
import com.filgrama.oauth.state.InvalidStateException;
import com.filgrama.oauth.state.OAuthState;
import com.filgrama.oauth.state.OAuthStateService;
import com.filgrama.repository.AccountCredentialRepository;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Onboarding por OAuth y gestión de cuentas sociales. Canje server-side, guardado
 * cifrado de credenciales y nunca expone tokens.
 *
 * <p>El {@code callback} resuelve a una URL de redirect (no problem+json), porque lo
 * consume el navegador del cliente; el resto lanza {@link ApiException} (RFC 7807).
 */
@Service
public class AccountService {

    private final SocialAccountRepository accountRepo;
    private final AccountCredentialRepository credentialRepo;
    private final ClientRepository clientRepo;
    private final OAuthProviderRegistry providers;
    private final OAuthStateService stateService;
    private final TokenCipher cipher;
    private final OAuthProperties props;

    public AccountService(SocialAccountRepository accountRepo,
                          AccountCredentialRepository credentialRepo,
                          ClientRepository clientRepo,
                          OAuthProviderRegistry providers,
                          OAuthStateService stateService,
                          TokenCipher cipher,
                          OAuthProperties props) {
        this.accountRepo = accountRepo;
        this.credentialRepo = credentialRepo;
        this.clientRepo = clientRepo;
        this.providers = providers;
        this.stateService = stateService;
        this.cipher = cipher;
        this.props = props;
    }

    // ---- Lecturas (sin tokens) ----

    @Transactional(readOnly = true)
    public List<AccountResponse> listByClient(Long clientId) {
        if (!clientRepo.existsById(clientId)) {
            throw ApiException.notFound("Client %d not found".formatted(clientId));
        }
        return accountRepo.findByClientId(clientId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long accountId) {
        return AccountResponse.from(loadAccount(accountId));
    }

    // ---- connect ----

    @Transactional(readOnly = true)
    public ConnectResponse connect(Long clientId, String platformPath, Long userId) {
        if (!clientRepo.existsById(clientId)) {
            throw ApiException.notFound("Client %d not found".formatted(clientId));
        }
        Platform platform = Platforms.fromPath(platformPath)
                .orElseThrow(() -> ApiException.badRequest("Plataforma inválida: " + platformPath));

        String state = stateService.issue(clientId, platform, userId);
        String authorizationUrl = providers.forPlatform(platform).buildAuthorizationUrl(platform, state);
        return new ConnectResponse(authorizationUrl, state);
    }

    // ---- callback (redirect, no problem+json) ----

    /**
     * Valida {@code state}, canja el {@code code} server-side, crea/actualiza la cuenta +
     * credencial cifrada y devuelve la URL del front a la que redirigir
     * ({@code ?accountId=} o {@code ?error=}). Nunca lanza: cualquier falla → redirect con error.
     */
    @Transactional
    public String completeCallback(String platformPath, String code, String state) {
        Platform platform = Platforms.fromPath(platformPath).orElse(null);
        if (platform == null) {
            return errorRedirect("invalid_platform");
        }

        OAuthState validated;
        try {
            validated = stateService.consume(state);
        } catch (InvalidStateException e) {
            return errorRedirect("invalid_state");
        }
        // El state ata el callback a su red de origen.
        if (validated.platform() != platform) {
            return errorRedirect("invalid_state");
        }

        OAuthExchangeResult result;
        try {
            result = providers.forPlatform(platform).exchangeCode(platform, code);
        } catch (TokenRevokedException e) {
            return errorRedirect("token_revoked");
        } catch (OAuthException e) {
            return errorRedirect("exchange_failed");
        }

        // Cuenta personal de IG/FB → UNSUPPORTED, sin credencial.
        boolean meta = platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK;
        if (meta && result.accountType() == AccountType.PERSONAL) {
            upsertAccount(validated.clientId(), platform, result,
                    AccountStatus.UNSUPPORTED, validated.userId());
            return errorRedirect("unsupported_personal");
        }

        SocialAccount account = upsertAccount(validated.clientId(), platform, result,
                AccountStatus.CONNECTED, validated.userId());
        saveCredential(account.getId(), result.accessToken(), result.refreshToken(),
                result.tokenType(), result.scopes(), result.expiresAt());
        return successRedirect(account.getId());
    }

    // ---- disconnect ----

    @Transactional
    public void disconnect(Long accountId) {
        SocialAccount account = loadAccount(accountId);
        account.setStatus(AccountStatus.DISCONNECTED);
        accountRepo.save(account);
    }

    // ---- refresh-token [ADMIN] ----

    @Transactional
    public AccountResponse refreshToken(Long accountId) {
        SocialAccount account = loadAccount(accountId);
        AccountCredential cred = credentialRepo.findById(accountId)
                .orElseThrow(() -> ApiException.unprocessable(
                        "La cuenta %d no tiene credencial para refrescar".formatted(accountId)));

        String access = cipher.decrypt(cred.getAccessTokenEnc());
        String refresh = cred.getRefreshTokenEnc() != null ? cipher.decrypt(cred.getRefreshTokenEnc()) : null;

        OAuthRefreshResult refreshed;
        try {
            refreshed = providers.forPlatform(account.getPlatform())
                    .refreshToken(account.getPlatform(), access, refresh);
        } catch (TokenRevokedException e) {
            // Autorización revocada → marcar para re-onboarding.
            account.setStatus(AccountStatus.ERROR);
            accountRepo.save(account);
            throw ApiException.unprocessable("Token revocado; la cuenta requiere re-conexión");
        } catch (OAuthException e) {
            throw ApiException.unprocessable("No se pudo refrescar el token");
        }

        cred.setAccessTokenEnc(cipher.encrypt(refreshed.accessToken()));
        if (refreshed.refreshToken() != null) { // rotación (TikTok)
            cred.setRefreshTokenEnc(cipher.encrypt(refreshed.refreshToken()));
        }
        if (refreshed.scopes() != null) {
            cred.setScopes(refreshed.scopes());
        }
        cred.setExpiresAt(refreshed.expiresAt());
        cred.setLastRefreshedAt(Instant.now());
        credentialRepo.save(cred);

        // TAREA A: corrige nombre/handle reales en cada refresh (también en el sync diario), sin reconectar.
        boolean accountChanged = applyProfile(account, refreshed.accessToken());
        if (account.getStatus() != AccountStatus.CONNECTED) {
            account.setStatus(AccountStatus.CONNECTED);
            accountChanged = true;
        }
        if (accountChanged) {
            accountRepo.save(account);
        }
        return AccountResponse.from(account);
    }

    /**
     * Best-effort: refresca nombre visible + handle desde el provider (user-info de la red) con el
     * access token vigente. Sólo pisa campos que la red devuelve no-null y nunca rompe el refresh —
     * si el provider no expone perfil (Meta/mock) o la llamada falla, deja la cuenta como estaba.
     *
     * @return {@code true} si cambió algún campo de la cuenta (para decidir si persistir).
     */
    private boolean applyProfile(SocialAccount account, String accessToken) {
        OAuthProfile profile;
        try {
            profile = providers.forPlatform(account.getPlatform())
                    .fetchProfile(account.getPlatform(), accessToken)
                    .orElse(null);
        } catch (OAuthException e) {
            return false;
        }
        if (profile == null) {
            return false;
        }
        boolean changed = false;
        if (profile.handle() != null && !profile.handle().equals(account.getHandle())) {
            account.setHandle(profile.handle());
            changed = true;
        }
        if (profile.displayName() != null && !profile.displayName().equals(account.getDisplayName())) {
            account.setDisplayName(profile.displayName());
            changed = true;
        }
        return changed;
    }

    // ---- helpers ----

    private SocialAccount loadAccount(Long accountId) {
        return accountRepo.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account %d not found".formatted(accountId)));
    }

    /** Reusa la fila existente (UNIQUE platform+external_account_id) o crea una nueva. */
    private SocialAccount upsertAccount(Long clientId, Platform platform, OAuthExchangeResult r,
                                        AccountStatus status, Long connectedBy) {
        SocialAccount account = accountRepo
                .findByPlatformAndExternalAccountId(platform, r.externalAccountId())
                .orElseGet(SocialAccount::new);
        boolean isNew = account.getId() == null;

        account.setClientId(clientId);
        account.setPlatform(platform);
        account.setExternalAccountId(r.externalAccountId());
        account.setHandle(r.handle());
        account.setDisplayName(r.displayName());
        account.setAccountType(r.accountType());
        account.setCapabilities(r.capabilities());
        account.setCapabilitiesCheckedAt(Instant.now());
        account.setStatus(status);
        if (connectedBy != null) {
            account.setConnectedBy(connectedBy);
        }
        if (isNew) {
            account.setConnectedAt(Instant.now()); // columna no-updatable: solo al crear
        }
        return accountRepo.save(account);
    }

    private void saveCredential(Long accountId, String accessToken, String refreshToken,
                                String tokenType, String scopes, Instant expiresAt) {
        AccountCredential cred = credentialRepo.findById(accountId).orElseGet(AccountCredential::new);
        cred.setAccountId(accountId);
        cred.setAccessTokenEnc(cipher.encrypt(accessToken));
        cred.setRefreshTokenEnc(refreshToken != null ? cipher.encrypt(refreshToken) : null);
        cred.setTokenType(tokenType);
        cred.setScopes(scopes);
        cred.setExpiresAt(expiresAt);
        cred.setLastRefreshedAt(Instant.now());
        credentialRepo.save(cred);
    }

    private String successRedirect(Long accountId) {
        return UriComponentsBuilder.fromUriString(props.getFrontRedirectUrl())
                .queryParam("accountId", accountId)
                .encode().toUriString();
    }

    private String errorRedirect(String errorCode) {
        return UriComponentsBuilder.fromUriString(props.getFrontRedirectUrl())
                .queryParam("error", errorCode)
                .encode().toUriString();
    }
}
