package com.filgrama.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.filgrama.auth.web.dto.LoginResponse;

/**
 * Base de los tests e2e: levanta la app COMPLETA ({@code RANDOM_PORT}) contra un Postgres real
 * efímero (Testcontainers). Flyway corre las migraciones (V1/V2/V3) y el {@code AdminSeedRunner}
 * siembra el admin de dev — la red de seguridad prueba el sistema integrado de punta a punta.
 *
 * <p><b>Patrón "singleton container":</b> el contenedor se arranca UNA vez en el bloque estático
 * (no usamos {@code @Container}/{@code @Testcontainers}). Así Spring cachea un único contexto y
 * reusa el mismo Postgres entre TODAS las clases de test del suite; el reaper (Ryuk) lo limpia al
 * terminar la JVM. Con {@code @Container} el contenedor se pararía tras la primera clase y rompería
 * el contexto cacheado de las siguientes. {@code @ServiceConnection} basta para que Boot
 * autoconfigure el datasource hacia él (no requiere {@code @Testcontainers}).
 *
 * <p>Storage en modo {@code local} (carpeta temporal) para no depender de MinIO en el e2e.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractE2ETest {

    static final String ADMIN_EMAIL = "admin@filgrama.local";
    static final String ADMIN_PASSWORD = "Admin123!";

    // Testcontainers 2.x: la clase canónica vive en org.testcontainers.postgresql y ya no es genérica.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static final String STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("filgrama-e2e-media").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.backend", () -> "local");
        registry.add("storage.local.base-dir", () -> STORAGE_DIR);
    }

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // JDK HttpClient: soporta PATCH (el SimpleClientHttpRequestFactory no).
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    /**
     * HTTP genérico que NUNCA lanza por status 4xx/5xx (suprime el manejo de error por defecto del
     * RestClient) para poder afirmar sobre 401/403/404. Deserializa el body al tipo pedido vía los
     * message converters (usá {@code Map.class} para inspeccionar problem+json, o un DTO/record).
     */
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
                .onStatus(status -> true, (request, response) -> { /* no lanzar: queremos el status */ })
                .toEntity(responseType);
    }

    protected <T> ResponseEntity<T> get(String path, String bearerToken, Class<T> responseType) {
        return exchange(HttpMethod.GET, path, null, bearerToken, responseType);
    }

    protected <T> ResponseEntity<T> post(String path, Object body, String bearerToken, Class<T> responseType) {
        return exchange(HttpMethod.POST, path, body, bearerToken, responseType);
    }

    protected <T> ResponseEntity<T> patch(String path, Object body, String bearerToken, Class<T> responseType) {
        return exchange(HttpMethod.PATCH, path, body, bearerToken, responseType);
    }

    /** Login completo; devuelve el par de tokens + usuario, o falla la aserción si no es 200. */
    protected LoginResponse login(String email, String password) {
        ResponseEntity<LoginResponse> res = post("/api/v1/auth/login",
                Map.of("email", email, "password", password), null, LoginResponse.class);
        if (res.getStatusCode().value() != 200 || res.getBody() == null) {
            throw new AssertionError("login esperado 200 para " + email + " pero fue " + res.getStatusCode());
        }
        return res.getBody();
    }

    /** Access token del admin seed de dev. */
    protected String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    }
}
