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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.filgrama.account.dto.AccountResponse;
import com.filgrama.account.dto.ConnectResponse;
import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.oauth.OAuthProviderRegistry;
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

    private final TokenCipher cipher = new TokenCipher("MjciXevgsHmpyP3wf6vOZRS17GPYQGc7EJwFbeuW9YM=");
    private OAuthStateService stateService;
    private AccountService service;

    @BeforeEach
    void setup() {
        stateService = new OAuthStateService(STATE_SECRET, 600L);
        OAuthProviderRegistry registry = new OAuthProviderRegistry(List.of(new MockOAuthProvider()));
        service = new AccountService(accountRepo, credentialRepo, clientRepo, registry,
                stateService, cipher, new OAuthProperties());
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
    void accountResponseNeverExposesTokens() {
        boolean leaks = Arrays.stream(AccountResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(n -> n.toLowerCase().contains("token"));
        assertThat(leaks).as("AccountResponse no debe exponer campos de token").isFalse();
    }
}
