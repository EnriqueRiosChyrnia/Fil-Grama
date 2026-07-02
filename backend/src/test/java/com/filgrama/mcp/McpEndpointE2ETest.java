package com.filgrama.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.filgrama.auth.JwtService;
import com.filgrama.domain.Client;
import com.filgrama.domain.User;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.UserRepository;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import tools.jackson.databind.json.JsonMapper;

/**
 * Integración del endpoint {@code /mcp} de punta a punta contra el servidor real (RANDOM_PORT) usando
 * el cliente oficial del MCP Java SDK sobre Streamable HTTP — el mismo transporte que usa
 * {@code claude mcp add --transport http} (spec/08 §Decisiones T5). Verifica: (1) sin token → 401
 * (el endpoint NO es público); (2) con JWT Bearer, el handshake y {@code tools/list} exponen las 7
 * tools; (3) un {@code tools/call} real ({@code list_clients}) devuelve datos dentro del scope.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpEndpointE2ETest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static final String STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("filgrama-mcp-e2e").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("storage.backend", () -> "local");
        registry.add("storage.local.base-dir", () -> STORAGE_DIR);
    }

    private static final String CLIENT_NAME = "Oh My Bunny E2E";
    private static boolean seeded;

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired ClientRepository clients;

    @BeforeEach
    void seed() {
        if (seeded) {
            return;
        }
        Client c = new Client();
        c.setName(CLIENT_NAME);
        c.setTimezone("America/Asuncion");
        clients.save(c);
        seeded = true;
    }

    @Test
    void mcpEndpoint_requiresJwt() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void listsToolsAndCallsListClients_withJwt() {
        try (McpSyncClient client = mcpClient(adminToken())) {
            client.initialize();

            List<Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(Tool::name).contains(
                    "list_clients", "get_client_report_data", "get_metric_series",
                    "get_audience_demographics", "compare_periods", "get_posting_performance",
                    "save_report_narrative");

            // El schema expone los parámetros con los nombres del contrato (snake_case) → confirma que
            // el compilador retiene -parameters y que la tool es usable tal como la documenta spec/08.
            Tool reportTool = tools.stream()
                    .filter(t -> "get_client_report_data".equals(t.name())).findFirst().orElseThrow();
            assertThat(reportTool.inputSchema().toString()).contains("client_id").contains("period");

            CallToolResult result = client.callTool(new CallToolRequest("list_clients", Map.of()));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(payload(result)).contains(CLIENT_NAME);
        }
    }

    private McpSyncClient mcpClient(String token) {
        McpJsonMapper mapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        McpSyncHttpClientRequestCustomizer authHeader =
                (builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + token);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUrl())
                .endpoint("/mcp")
                .jsonMapper(mapper)
                .httpRequestCustomizer(authHeader)
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(20))
                .build();
    }

    /** Texto + contenido estructurado de la respuesta de la tool, para aserciones robustas. */
    private static String payload(CallToolResult result) {
        StringBuilder sb = new StringBuilder(String.valueOf(result.structuredContent()));
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    private String adminToken() {
        User admin = users.findByEmail("admin@filgrama.local").orElseThrow();
        return jwtService.issueAccessToken(admin.getId(), admin.getRole());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
