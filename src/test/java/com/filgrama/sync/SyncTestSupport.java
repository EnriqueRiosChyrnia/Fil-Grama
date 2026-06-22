package com.filgrama.sync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.filgrama.auth.web.dto.LoginResponse;
import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.crypto.TokenCipher;
import com.filgrama.repository.AccountCredentialRepository;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.RawApiPayloadRepository;
import com.filgrama.repository.SocialAccountRepository;
import com.filgrama.repository.SyncAccountResultRepository;
import com.filgrama.repository.SyncRunRepository;
import com.filgrama.sync.capture.MockInsightsProvider;
import com.filgrama.sync.job.SyncService;

/**
 * Base de los tests del job: app completa contra un Postgres real efímero (Testcontainers,
 * patrón singleton container como el suite e2e). Perfil {@code test} → activa el
 * {@link MockInsightsProvider} y el MockOAuthProvider; desactiva el scheduler; storage en modo
 * local (temp) para no depender de MinIO. Flyway siembra el catálogo de métricas (V3) y el
 * AdminSeedRunner el admin de dev.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class SyncTestSupport {

    static final String ADMIN_EMAIL = "admin@filgrama.local";
    static final String ADMIN_PASSWORD = "Admin123!";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static final String STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("filgrama-sync-media").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("storage.backend", () -> "local");
        registry.add("storage.local.base-dir", () -> STORAGE_DIR);
        registry.add("sync.scheduler.enabled", () -> "false");
        registry.add("sync.retry.backoff-millis", () -> "0");
        registry.add("sync.account.timeout-seconds", () -> "30");
        registry.add("sync.concurrency", () -> "1");
    }

    @LocalServerPort
    int port;

    @Autowired protected SyncService syncService;
    @Autowired protected MockInsightsProvider mockProvider;
    @Autowired protected SnapshotUpsertRepository upsertRepository;
    @Autowired protected TokenCipher tokenCipher;
    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected ClientRepository clientRepository;
    @Autowired protected SocialAccountRepository socialAccountRepository;
    @Autowired protected AccountCredentialRepository accountCredentialRepository;
    @Autowired protected SyncRunRepository syncRunRepository;
    @Autowired protected SyncAccountResultRepository syncAccountResultRepository;
    @Autowired protected RawApiPayloadRepository rawApiPayloadRepository;
    @Autowired protected AccountMetricSnapshotRepository accountSnapshotRepository;
    @Autowired protected PostMetricSnapshotRepository postSnapshotRepository;
    @Autowired protected PostRepository postRepository;
    @Autowired protected MediaAssetRepository mediaAssetRepository;

    @BeforeEach
    void resetState() {
        // Truncado en orden seguro vía CASCADE; NO toca users (admin seed) ni metrics (catálogo Flyway).
        jdbc.execute("""
                TRUNCATE TABLE sync_account_results, raw_api_payloads, account_metric_snapshots,
                    post_metric_snapshots, media_assets, posts, account_credentials, social_accounts,
                    sync_runs, clients
                RESTART IDENTITY CASCADE
                """);
        mockProvider.setSeed(1_000L);
    }

    // ---- seeding ----

    protected Client newClient(String timezone) {
        Client client = new Client();
        client.setName("Cliente " + UUID.randomUUID());
        client.setTimezone(timezone);
        return clientRepository.save(client);
    }

    /** Cuenta CONNECTED + credencial con token cifrado. {@code expiresAt} controla el refresh. */
    protected SocialAccount connectAccount(Long clientId, Platform platform, String handle, Instant expiresAt) {
        SocialAccount account = new SocialAccount();
        account.setClientId(clientId);
        account.setPlatform(platform);
        account.setExternalAccountId("ext-" + UUID.randomUUID());
        account.setHandle(handle);
        account.setAccountType(AccountType.BUSINESS);
        account.setStatus(AccountStatus.CONNECTED);
        account.setConnectedAt(Instant.now());
        account = socialAccountRepository.save(account);

        AccountCredential cred = new AccountCredential();
        cred.setAccountId(account.getId());
        cred.setAccessTokenEnc(tokenCipher.encrypt("fake-access-token-" + account.getId()));
        cred.setTokenType("bearer");
        cred.setExpiresAt(expiresAt);
        accountCredentialRepository.save(cred);
        return account;
    }

    /** Cuenta CONNECTED con token sin vencimiento próximo (no fuerza refresh). */
    protected SocialAccount connectAccount(Long clientId, Platform platform, String handle) {
        return connectAccount(clientId, platform, handle, Instant.now().plus(365, ChronoUnit.DAYS));
    }

    // ---- HTTP (para el test de contrato/RBAC) ----

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    protected <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body,
            String bearerToken, Class<T> responseType) {
        RestClient.RequestBodySpec spec = client().method(method).uri(path);
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.retrieve()
                .onStatus(status -> true, (req, res) -> { /* no lanzar: queremos el status */ })
                .toEntity(responseType);
    }

    protected <T> ResponseEntity<T> get(String path, String bearerToken, Class<T> responseType) {
        return exchange(HttpMethod.GET, path, null, bearerToken, responseType);
    }

    protected <T> ResponseEntity<T> post(String path, Object body, String bearerToken, Class<T> responseType) {
        return exchange(HttpMethod.POST, path, body, bearerToken, responseType);
    }

    protected String adminToken() {
        ResponseEntity<LoginResponse> res = post("/api/v1/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), null, LoginResponse.class);
        if (res.getStatusCode().value() != 200 || res.getBody() == null) {
            throw new AssertionError("login admin esperado 200 pero fue " + res.getStatusCode());
        }
        return res.getBody().accessToken();
    }

    /** Crea un empleado vía API (admin) y devuelve su access token. */
    protected String employeeToken(String adminToken) {
        String email = "empleado-" + UUID.randomUUID() + "@filgrama.local";
        String password = "Empleado123!";
        ResponseEntity<Map> created = post("/api/v1/users",
                Map.of("email", email, "fullName", "Empleado Sync", "role", "EMPLEADO", "password", password),
                adminToken, Map.class);
        if (created.getStatusCode().value() != 201) {
            throw new AssertionError("alta empleado esperaba 201 pero fue " + created.getStatusCode());
        }
        ResponseEntity<LoginResponse> login = post("/api/v1/auth/login",
                Map.of("email", email, "password", password), null, LoginResponse.class);
        return login.getBody().accessToken();
    }

    protected static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
