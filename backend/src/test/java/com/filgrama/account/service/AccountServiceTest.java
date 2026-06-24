package com.filgrama.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import com.filgrama.account.event.AccountConnectedEvent;

import com.filgrama.account.dto.AccountResponse;
import com.filgrama.account.dto.ConnectResponse;
import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthProfile;
import com.filgrama.oauth.OAuthProvider;
import com.filgrama.oauth.OAuthProviderRegistry;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.config.OAuthProperties;
import com.filgrama.oauth.crypto.TokenCipher;
import com.filgrama.oauth.provider.MockOAuthProvider;
import com.filgrama.oauth.state.OAuthStateService;
import com.filgrama.repository.AccountCredentialRepository;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Flujo end-to-end a nivel servicio con el {@code MockOAuthProvider}, {@code TokenCipher}
 * y {@code OAuthStateService} reales; repositorios mockeados (sin DB).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String STATE_SECRET = "wmKWIhsMq9NtiXJnItX8oVx4u01AQ6MkjccOO0OtD50=";

    @Mock SocialAccountRepository accountRepo;
    @Mock AccountCredentialRepository credentialRepo;
    @Mock ClientRepository clientRepo;
    @Mock ApplicationEventPublisher events;

    private final TokenCipher cipher = new TokenCipher("MjciXevgsHmpyP3wf6vOZRS17GPYQGc7EJwFbeuW9YM=");
    private OAuthStateService stateService;
    private AccountService service;

    @BeforeEach
    void setup() {
        stateService = new OAuthStateService(STATE_SECRET, 600L);
        OAuthProviderRegistry registry = new OAuthProviderRegistry(List.of(new MockOAuthProvider()));
        service = new AccountService(accountRepo, credentialRepo, clientRepo, registry,
                stateService, cipher, new OAuthProperties(), events);
    }

    @Test
    void connectReturnsAuthorizationUrlAndState() {
        when(clientRepo.existsById(1L)).thenReturn(true);

        ConnectResponse resp = service.connect(1L, "tiktok", 7L);

        assertThat(resp.authorizationUrl()).contains("mock.oauth.local").contains("tiktok");
        assertThat(resp.state()).isNotBlank();
        // El state emitido es válido y trae el origen correcto.
        assertThat(stateService.consume(resp.state()).clientId()).isEqualTo(1L);
    }

    @Test
    void connectUnknownClientThrowsNotFound() {
        when(clientRepo.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.connect(99L, "tiktok", 7L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void connectInvalidPlatformThrowsBadRequest() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.connect(1L, "myspace", 7L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void callbackValidStateCreatesAccountAndEncryptedCredential() {
        when(accountRepo.findByPlatformAndExternalAccountId(eq(Platform.TIKTOK), anyString()))
                .thenReturn(Optional.empty());
        when(accountRepo.save(any())).thenAnswer(inv -> {
            SocialAccount a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });
        when(credentialRepo.findById(10L)).thenReturn(Optional.empty());
        ArgumentCaptor<AccountCredential> credCaptor = ArgumentCaptor.forClass(AccountCredential.class);

        String state = stateService.issue(1L, Platform.TIKTOK, 7L);
        String target = service.completeCallback("tiktok", "good-code", state);

        assertThat(target).contains("accountId=10");

        ArgumentCaptor<SocialAccount> accCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(accountRepo).save(accCaptor.capture());
        assertThat(accCaptor.getValue().getStatus()).isEqualTo(AccountStatus.CONNECTED);
        assertThat(accCaptor.getValue().getConnectedBy()).isEqualTo(7L);

        verify(credentialRepo).save(credCaptor.capture());
        AccountCredential cred = credCaptor.getValue();
        assertThat(cred.getAccessTokenEnc()).isNotNull();
        // El token persistido está cifrado (no es el texto plano) pero descifra al original.
        assertThat(new String(cred.getAccessTokenEnc(), StandardCharsets.ISO_8859_1))
                .doesNotContain("mock-access");
        assertThat(cipher.decrypt(cred.getAccessTokenEnc())).startsWith("mock-access");
        assertThat(cred.getRefreshTokenEnc()).isNotNull(); // TikTok rota refresh
    }

    @Test
    void callbackReusedStateCreatesNothing() {
        String state = stateService.issue(1L, Platform.TIKTOK, 7L);
        stateService.consume(state); // ya usado

        String target = service.completeCallback("tiktok", "good-code", state);

        assertThat(target).contains("error=invalid_state");
        verify(accountRepo, never()).save(any());
        verify(credentialRepo, never()).save(any());
    }

    @Test
    void callbackPersonalInstagramIsUnsupportedWithoutCredential() {
        when(accountRepo.findByPlatformAndExternalAccountId(eq(Platform.INSTAGRAM), anyString()))
                .thenReturn(Optional.empty());
        when(accountRepo.save(any())).thenAnswer(inv -> {
            SocialAccount a = inv.getArgument(0);
            a.setId(11L);
            return a;
        });

        String state = stateService.issue(1L, Platform.INSTAGRAM, 7L);
        String target = service.completeCallback("instagram", "personal-1", state);

        assertThat(target).contains("error=unsupported_personal");

        ArgumentCaptor<SocialAccount> accCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(accountRepo).save(accCaptor.capture());
        assertThat(accCaptor.getValue().getStatus()).isEqualTo(AccountStatus.UNSUPPORTED);
        verify(credentialRepo, never()).save(any());
    }

    @Test
    void callbackInvalidPlatformRedirectsError() {
        String target = service.completeCallback("myspace", "code", "whatever");
        assertThat(target).contains("error=invalid_platform");
        verify(accountRepo, never()).save(any());
    }

    // ---- TAREA A: scan al conectar dispara el evento ----

    @Test
    void callbackExitosoPublicaAccountConnectedEvent() {
        when(accountRepo.findByPlatformAndExternalAccountId(eq(Platform.TIKTOK), anyString()))
                .thenReturn(Optional.empty());
        when(accountRepo.save(any())).thenAnswer(inv -> {
            SocialAccount a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });
        when(credentialRepo.findById(10L)).thenReturn(Optional.empty());

        String state = stateService.issue(1L, Platform.TIKTOK, 7L);
        service.completeCallback("tiktok", "good-code", state);

        verify(events).publishEvent(new AccountConnectedEvent(10L));
    }

    @Test
    void callbackUnsupportedNoPublicaEvento() {
        when(accountRepo.findByPlatformAndExternalAccountId(eq(Platform.INSTAGRAM), anyString()))
                .thenReturn(Optional.empty());
        when(accountRepo.save(any())).thenAnswer(inv -> {
            SocialAccount a = inv.getArgument(0);
            a.setId(11L);
            return a;
        });

        String state = stateService.issue(1L, Platform.INSTAGRAM, 7L);
        service.completeCallback("instagram", "personal-1", state);

        verify(events, never()).publishEvent(any());
    }

    // ---- TAREA B: guard de reconexión (open_id esperado) ----

    @Test
    void reconnectMismatchRechazaConflictSinLinkearNiPublicar() {
        // Cuenta conocida que se quiere reconectar (open_id "open-A").
        SocialAccount expected = new SocialAccount();
        expected.setId(20L);
        expected.setClientId(1L);
        expected.setPlatform(Platform.TIKTOK);
        expected.setExternalAccountId("open-A");
        expected.setHandle("@cuentaA");
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(accountRepo.findById(20L)).thenReturn(Optional.of(expected));
        // Para el mensaje legible @X.
        when(accountRepo.findByPlatformAndExternalAccountId(Platform.TIKTOK, "open-A"))
                .thenReturn(Optional.of(expected));

        ConnectResponse resp = service.connect(1L, "tiktok", 7L, 20L);
        // El navegador autoriza OTRA cuenta: "code-B" da un open_id distinto en el mock.
        String state = resp.state();

        assertThatThrownBy(() -> service.completeCallback("tiktok", "code-B", state))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("@cuentaA")
                .hasMessageContaining("TikTok");

        // No linkea, no duplica, no dispara scan.
        verify(accountRepo, never()).save(any());
        verify(credentialRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void reconnectMismaCuentaSiLinkeaYPublica() {
        // open_id que el mock devolverá para "good-code" en TikTok (determinista).
        String openId = "mock-tiktok-" + Integer.toHexString("good-code".hashCode());
        SocialAccount existing = new SocialAccount();
        existing.setId(30L);
        existing.setClientId(1L);
        existing.setPlatform(Platform.TIKTOK);
        existing.setExternalAccountId(openId);
        existing.setHandle("@misma");
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(accountRepo.findById(30L)).thenReturn(Optional.of(existing));
        when(accountRepo.findByPlatformAndExternalAccountId(Platform.TIKTOK, openId))
                .thenReturn(Optional.of(existing));
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(credentialRepo.findById(30L)).thenReturn(Optional.empty());

        ConnectResponse resp = service.connect(1L, "tiktok", 7L, 30L);
        String target = service.completeCallback("tiktok", "good-code", resp.state());

        assertThat(target).contains("accountId=30");
        verify(accountRepo).save(any());
        verify(credentialRepo).save(any());
        verify(events).publishEvent(new AccountConnectedEvent(30L));
    }

    @Test
    void connectReconnectCuentaDeOtroClienteRechaza() {
        SocialAccount otherClient = new SocialAccount();
        otherClient.setId(40L);
        otherClient.setClientId(999L); // no es el cliente 1
        otherClient.setPlatform(Platform.TIKTOK);
        otherClient.setExternalAccountId("open-x");
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(accountRepo.findById(40L)).thenReturn(Optional.of(otherClient));

        assertThatThrownBy(() -> service.connect(1L, "tiktok", 7L, 40L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void disconnectSetsStatusDisconnected() {
        SocialAccount acc = new SocialAccount();
        acc.setId(5L);
        acc.setStatus(AccountStatus.CONNECTED);
        when(accountRepo.findById(5L)).thenReturn(Optional.of(acc));

        service.disconnect(5L);

        assertThat(acc.getStatus()).isEqualTo(AccountStatus.DISCONNECTED);
        verify(accountRepo).save(acc);
    }

    @Test
    void disconnectUnknownAccountThrowsNotFound() {
        when(accountRepo.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.disconnect(404L)).isInstanceOf(ApiException.class);
    }

    @Test
    void refreshTokenAlsoCorrectsDisplayNameAndHandle() {
        // TAREA A: el refresh/sync corrige el nombre de cuentas viejas (ej. account 17) sin reconectar.
        AccountService svc = new AccountService(accountRepo, credentialRepo, clientRepo,
                new OAuthProviderRegistry(List.of(new ProfileFakeProvider())),
                stateService, cipher, new OAuthProperties(), events);

        SocialAccount account = new SocialAccount();
        account.setId(17L);
        account.setPlatform(Platform.TIKTOK);
        account.setStatus(AccountStatus.CONNECTED);
        account.setHandle("oid-legacy");                 // estado viejo: open_id como handle
        account.setDisplayName("TikTok oid-legacy");
        when(accountRepo.findById(17L)).thenReturn(Optional.of(account));
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountCredential cred = new AccountCredential();
        cred.setAccountId(17L);
        cred.setAccessTokenEnc(cipher.encrypt("old-access"));
        cred.setRefreshTokenEnc(cipher.encrypt("old-refresh"));
        when(credentialRepo.findById(17L)).thenReturn(Optional.of(cred));
        when(credentialRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.refreshToken(17L);

        assertThat(account.getHandle()).isEqualTo("@cebandotertulias");
        assertThat(account.getDisplayName()).isEqualTo("Cebando Tertulias");
        verify(accountRepo).save(account);
    }

    /** Provider de prueba: refresca token y expone un perfil real (lo que hace TikTok en prod). */
    private static final class ProfileFakeProvider implements OAuthProvider {
        @Override
        public boolean supports(Platform platform) {
            return platform == Platform.TIKTOK;
        }

        @Override
        public String buildAuthorizationUrl(Platform platform, String state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OAuthExchangeResult exchangeCode(Platform platform, String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken) {
            return new OAuthRefreshResult("new-access", "new-refresh", "bearer", "user.info.profile",
                    Instant.now().plusSeconds(86400));
        }

        @Override
        public Optional<OAuthProfile> fetchProfile(Platform platform, String accessToken) {
            return Optional.of(new OAuthProfile("@cebandotertulias", "Cebando Tertulias", "https://cdn/x.jpg"));
        }
    }

    @Test
    void accountResponseNeverExposesTokens() {
        boolean leaks = Arrays.stream(AccountResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(n -> n.toLowerCase().contains("token"));
        assertThat(leaks).as("AccountResponse no debe exponer campos de token").isFalse();
    }
}
