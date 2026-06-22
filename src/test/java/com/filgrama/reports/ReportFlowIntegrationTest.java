package com.filgrama.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.filgrama.auth.JwtService;
import com.filgrama.domain.Client;
import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.Post;
import com.filgrama.domain.PostMetricSnapshot;
import com.filgrama.domain.AccountMetricSnapshot;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.User;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;
import com.filgrama.repository.UserRepository;
import com.filgrama.storage.StoragePort;

/**
 * Integración de punta a punta del track Reportes contra un Postgres real (Testcontainers) y storage
 * local: Flyway aplica V1-V4, se siembran cliente/cuenta/posts/snapshots/miniatura y se ejercita el
 * flujo HTTP (POST genera, GET descarga). Cubre la "definición de terminado": SUMMARY/MARKDOWN y
 * EXTENDED/PDF generados y descargables, rango inválido → 4xx, reporte de otro cliente → 404
 * (multi-tenant) y narrativa ausente → sin sección "Análisis del mes".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ReportFlowIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static final String STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("filgrama-reports-it").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("storage.backend", () -> "local");
        registry.add("storage.local.base-dir", () -> STORAGE_DIR);
    }

    /** PNG 1x1 (miniatura cacheada de prueba). */
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private static boolean seeded;
    private static Long clientId;
    private static Long otherClientId;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired ClientRepository clients;
    @Autowired SocialAccountRepository accounts;
    @Autowired PostRepository posts;
    @Autowired PostMetricSnapshotRepository postSnapshots;
    @Autowired AccountMetricSnapshotRepository accountSnapshots;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired StoragePort storage;
    @Autowired ReportRepository reports;

    @BeforeEach
    void seed() {
        if (seeded) {
            return;
        }
        Client target = newClient("Molinos del Sur");
        clientId = clients.save(target).getId();
        otherClientId = clients.save(newClient("Otro Cliente")).getId();

        SocialAccount ig = new SocialAccount();
        ig.setClientId(clientId);
        ig.setPlatform(Platform.INSTAGRAM);
        ig.setExternalAccountId("ig-" + clientId);
        ig.setHandle("@molinos");
        ig.setConnectedAt(Instant.now());
        Long accountId = accounts.save(ig).getId();

        // Cuenta: alcance + interacciones + seguidores (KPIs del SUMMARY + engagement).
        accountSnapshot(accountId, "ig_reach", "125400", LocalDate.parse("2026-05-15"));
        accountSnapshot(accountId, "ig_total_interactions", "9300", LocalDate.parse("2026-05-15"));
        accountSnapshot(accountId, "ig_followers_count", "5200", LocalDate.parse("2026-05-15"));

        // Tres publicaciones en el período: 2 Reels + 1 Feed, con alcance de post (rankBy=reach).
        Long reelA = newPost(accountId, PostType.REEL, "reelA", Instant.parse("2026-05-20T13:00:00Z"));
        Long reelB = newPost(accountId, PostType.REEL, "reelB", Instant.parse("2026-05-10T12:00:00Z"));
        Long feedA = newPost(accountId, PostType.IMAGE, "feedA", Instant.parse("2026-05-18T15:00:00Z"));
        postSnapshot(accountId, reelA, "ig_post_reach", "42000", LocalDate.parse("2026-05-21"));
        postSnapshot(accountId, reelB, "ig_post_reach", "21000", LocalDate.parse("2026-05-11"));
        postSnapshot(accountId, feedA, "ig_post_reach", "31000", LocalDate.parse("2026-05-19"));

        // Miniatura cacheada del reel destacado: bytes en storage + fila media_assets.
        String key = "clients/%d/posts/%d/thumb.png".formatted(clientId, reelA);
        storage.put(key, PNG_1X1, "image/png");
        MediaAsset asset = new MediaAsset();
        asset.setPostId(reelA);
        asset.setClientId(clientId);
        asset.setKind(MediaKind.THUMBNAIL);
        asset.setStoragePath(key);
        asset.setContentType("image/png");
        asset.setBytes(PNG_1X1.length);
        asset.setCapturedAt(Instant.now());
        mediaAssets.save(asset);

        seeded = true;
    }

    private Client newClient(String name) {
        Client c = new Client();
        c.setName(name);
        c.setTimezone("America/Asuncion");
        return c;
    }

    private Long newPost(Long accountId, PostType type, String ext, Instant publishedAt) {
        Post p = new Post();
        p.setClientId(clientId);
        p.setAccountId(accountId);
        p.setPlatform(Platform.INSTAGRAM);
        p.setExternalPostId(ext);
        p.setPostType(type);
        p.setPermalink("https://instagram.com/p/" + ext);
        p.setCaption("Publicación " + ext);
        p.setPublishedAt(publishedAt);
        p.setFirstSeenAt(Instant.now());
        return posts.save(p).getId();
    }

    private void accountSnapshot(Long accountId, String metric, String value, LocalDate date) {
        AccountMetricSnapshot s = new AccountMetricSnapshot();
        s.setClientId(clientId);
        s.setAccountId(accountId);
        s.setMetricKey(metric);
        s.setValue(new BigDecimal(value));
        s.setCapturedAt(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        s.setCaptureDate(date);
        accountSnapshots.save(s);
    }

    private void postSnapshot(Long accountId, Long postId, String metric, String value, LocalDate date) {
        PostMetricSnapshot s = new PostMetricSnapshot();
        s.setClientId(clientId);
        s.setAccountId(accountId);
        s.setPostId(postId);
        s.setMetricKey(metric);
        s.setValue(new BigDecimal(value));
        s.setCapturedAt(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        s.setCaptureDate(date);
        postSnapshots.save(s);
    }

    private String token() {
        User admin = users.findByEmail("admin@filgrama.local").orElseThrow();
        return jwtService.issueAccessToken(admin.getId(), admin.getRole());
    }

    private String body(String reportType, String format) {
        return """
                { "reportType":"%s", "format":"%s", "from":"2026-05-01", "to":"2026-05-31",
                  "platforms":["INSTAGRAM"], "rankBy":"reach" }
                """.formatted(reportType, format);
    }

    @Test
    void summaryMarkdownGeneratesAndDownloads() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/clients/{c}/reports", clientId)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(body("SUMMARY", "MARKDOWN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.downloadUrl").exists())
                .andReturn();

        long reportId = idFrom(created);
        assertThat(reports.findById(reportId)).isPresent()
                .get().satisfies(r -> assertThat(r.getStoragePath()).isNotNull());

        MvcResult dl = mvc.perform(get("/api/v1/clients/{c}/reports/{r}/download", clientId, reportId)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(dl.getResponse().getContentType()).startsWith("text/markdown");
        String md = dl.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Refleja período/redes/tipo y los KPIs reales; sin IA → sin "Análisis del mes".
        assertThat(md).contains("2026-05-01 a 2026-05-31").contains("INSTAGRAM").contains("KPIs por red");
        assertThat(md).contains("publicaciones");
        assertThat(md).doesNotContain("Análisis del mes");
    }

    @Test
    void extendedPdfGeneratesAndDownloads() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/clients/{c}/reports", clientId)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(body("EXTENDED", "PDF")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.format").value("PDF"))
                .andReturn();

        long reportId = idFrom(created);
        MvcResult dl = mvc.perform(get("/api/v1/clients/{c}/reports/{r}/download", clientId, reportId)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(dl.getResponse().getContentType()).isEqualTo("application/pdf");
        byte[] pdf = dl.getResponse().getContentAsByteArray();
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void invalidRangeIsRejected() throws Exception {
        String invalid = """
                { "reportType":"SUMMARY", "format":"MARKDOWN", "from":"2026-05-31", "to":"2026-05-01",
                  "platforms":["INSTAGRAM"], "rankBy":"reach" }
                """;
        mvc.perform(post("/api/v1/clients/{c}/reports", clientId)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void reportOfAnotherClientIsNotFound() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/clients/{c}/reports", clientId)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(body("SUMMARY", "MARKDOWN")))
                .andExpect(status().isCreated())
                .andReturn();
        long reportId = idFrom(created);

        // El mismo reporte pedido bajo OTRO cliente no debe aparecer (sin fuga entre tenants).
        mvc.perform(get("/api/v1/clients/{c}/reports/{r}", otherClientId, reportId)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/api/v1/clients/{c}/reports", clientId)
                        .contentType(MediaType.APPLICATION_JSON).content(body("SUMMARY", "MARKDOWN")))
                .andExpect(status().isUnauthorized());
    }

    private static long idFrom(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        // {"id":N,...} — parse barato sin dependencias extra.
        int i = json.indexOf("\"id\"");
        int colon = json.indexOf(':', i);
        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == ' ')) {
            end++;
        }
        return Long.parseLong(json.substring(colon + 1, end).trim());
    }
}
