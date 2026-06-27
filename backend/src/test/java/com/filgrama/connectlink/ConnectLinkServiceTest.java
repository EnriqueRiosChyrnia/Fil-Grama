package com.filgrama.connectlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.filgrama.connectlink.dto.AuthorizationUrlResponse;
import com.filgrama.connectlink.dto.ConnectLinkResponse;
import com.filgrama.connectlink.dto.ConnectLinkSummary;
import com.filgrama.connectlink.dto.PublicLinkInfo;
import com.filgrama.domain.Client;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.oauth.OAuthProviderRegistry;
import com.filgrama.oauth.provider.MockOAuthProvider;
import com.filgrama.oauth.state.OAuthOrigin;
import com.filgrama.oauth.state.OAuthState;
import com.filgrama.oauth.state.OAuthStateService;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Link compartible de conexión a nivel servicio: {@code OAuthStateService} y {@code MockOAuthProvider}
 * reales, repositorios mockeados (sin DB). Cubre CU9 (creación con token hasheado, vigencia 404/410,
 * arranque público con {@code origin=LINK}).
 */
@ExtendWith(MockitoExtension.class)
class ConnectLinkServiceTest {

    private static final String STATE_SECRET = "wmKWIhsMq9NtiXJnItX8oVx4u01AQ6MkjccOO0OtD50=";
    private static final String BASE_URL = "http://front.local/connect";

    @Mock ConnectLinkRepository linkRepo;
    @Mock ClientRepository clientRepo;
    @Mock SocialAccountRepository accountRepo;

    private OAuthStateService stateService;
    private ConnectLinkService service;

    @BeforeEach
    void setup() {
        stateService = new OAuthStateService(STATE_SECRET, 600L);
        OAuthProviderRegistry registry = new OAuthProviderRegistry(List.of(new MockOAuthProvider()));
        service = new ConnectLinkService(linkRepo, clientRepo, accountRepo, stateService, registry, BASE_URL);
    }

    // ---- crear ----

    @Test
    void createDevuelveTokenRawSoloEnRespuestaYGuardaSoloElHash() {
        Client client = new Client();
        client.setName("ACME");
        when(clientRepo.findById(1L)).thenReturn(Optional.of(client));
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConnectLinkResponse resp = service.create(1L, null, null, 7L);

        assertThat(resp.token()).isNotBlank();
        assertThat(resp.url()).isEqualTo(BASE_URL + "/" + resp.token());
        assertThat(resp.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<ConnectLink> captor = ArgumentCaptor.forClass(ConnectLink.class);
        verify(linkRepo).save(captor.capture());
        ConnectLink saved = captor.getValue();
        // En DB vive sólo el sha-256 del token; el raw nunca se persiste.
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(resp.token()));
        assertThat(saved.getTokenHash()).isNotEqualTo(resp.token());
        assertThat(saved.getClientId()).isEqualTo(1L);
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
    }

    @Test
    void listVigentesExcluyeVencidos() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        ConnectLink vigente = link(Instant.now().plusSeconds(3600));
        vigente.setId(1L);
        ConnectLink vencido = link(Instant.now().minusSeconds(60));
        vencido.setId(2L);
        when(linkRepo.findByClientIdAndRevokedAtIsNull(1L)).thenReturn(List.of(vigente, vencido));

        List<ConnectLinkSummary> result = service.listVigentes(1L);

        assertThat(result).extracting(ConnectLinkSummary::id).containsExactly(1L);
    }

    @Test
    void revokeByExpectedAccountMarcaRevokedAt() {
        ConnectLink pending = link(Instant.now().plusSeconds(3600));
        when(linkRepo.findByExpectedAccountIdAndRevokedAtIsNull(60L)).thenReturn(List.of(pending));
        when(linkRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeByExpectedAccount(60L);

        assertThat(pending.getRevokedAt()).isNotNull();
    }

    // ---- público: vigencia ----

    @Test
    void resolvePublic404SiNoExiste() {
        when(linkRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolvePublic("nope"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolvePublic410SiRevocado() {
        ConnectLink link = link(Instant.now().plusSeconds(3600));
        link.setRevokedAt(Instant.now());
        when(linkRepo.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        assertThatThrownBy(() -> service.resolvePublic("tok"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void resolvePublic410SiVencido() {
        ConnectLink link = link(Instant.now().minusSeconds(60));
        when(linkRepo.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        assertThatThrownBy(() -> service.resolvePublic("tok"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void resolvePublicOkDevuelveMetadatosDelCliente() {
        ConnectLink link = link(Instant.now().plusSeconds(3600));
        link.setClientId(5L);
        link.setPlatform(Platform.TIKTOK);
        when(linkRepo.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        Client client = new Client();
        client.setName("ACME");
        when(clientRepo.findById(5L)).thenReturn(Optional.of(client));

        PublicLinkInfo info = service.resolvePublic("tok");

        assertThat(info.clientName()).isEqualTo("ACME");
        assertThat(info.platform()).isEqualTo("TIKTOK");
    }

    // ---- público: arranque del OAuth ----

    @Test
    void startOauthArrancaOAuthConOriginLink() {
        ConnectLink link = link(Instant.now().plusSeconds(3600));
        link.setClientId(3L);
        link.setCreatedBy(9L);
        link.setPlatform(Platform.TIKTOK);
        when(linkRepo.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationUrlResponse resp = service.startOauth("tok", "tiktok");

        assertThat(resp.authorizationUrl()).contains("mock.oauth.local").contains("tiktok");
        // El state embebido lleva origin=LINK, el clientId del link y connected_by = created_by.
        String state = resp.authorizationUrl().replaceAll(".*state=", "");
        OAuthState consumed = stateService.consume(state);
        assertThat(consumed.origin()).isEqualTo(OAuthOrigin.LINK);
        assertThat(consumed.clientId()).isEqualTo(3L);
        assertThat(consumed.userId()).isEqualTo(9L);
        // Multi-uso: marca usedAt pero NO revoca.
        assertThat(link.getUsedAt()).isNotNull();
        assertThat(link.getRevokedAt()).isNull();
    }

    @Test
    void startOauthRedNoHabilitadaDevuelve400() {
        ConnectLink link = link(Instant.now().plusSeconds(3600));
        link.setPlatform(Platform.TIKTOK);
        when(linkRepo.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.startOauth("tok", "instagram"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- helpers ----

    private static ConnectLink link(Instant expiresAt) {
        ConnectLink l = new ConnectLink();
        l.setId(1L);
        l.setClientId(1L);
        l.setTokenHash("hash");
        l.setCreatedBy(7L);
        l.setExpiresAt(expiresAt);
        return l;
    }

    private static String sha256Hex(String raw) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
