package com.filgrama.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.filgrama.domain.AccountMetricSnapshot;
import com.filgrama.domain.Client;
import com.filgrama.domain.Post;
import com.filgrama.domain.PostMetricSnapshot;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.User;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.domain.enums.Role;
import com.filgrama.error.ApiException;
import com.filgrama.mcp.dto.ClientView;
import com.filgrama.mcp.dto.NarrativeSaved;
import com.filgrama.metrics.service.MetricReportService;
import com.filgrama.reports.Report;
import com.filgrama.reports.ReportNarrativeService;
import com.filgrama.reports.ReportQueryRepository;
import com.filgrama.reports.ReportRepository;
import com.filgrama.reports.ReportService;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.render.MarkdownRenderer;
import com.filgrama.reports.render.PdfRenderer;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;
import com.filgrama.repository.UserRepository;

/**
 * Scope de las 7 tools MCP (spec/08 §Decisiones T5). Regla v1: <b>single-tenant</b>, sin modelo de
 * acceso empleado→cliente — un EMPLEADO ve TODOS los clientes, igual que un ADMIN, igual que la app
 * REST/UI (spec/02: {@code employee_client_priority} es un favorito informativo, nunca un permiso).
 * Cubre dos cosas: (a) paridad empleado/admin en todas las tools de lectura contra un Postgres real, y
 * (b) que {@link ClientAccessService} sigue siendo el único choke point — con un stub que deniega,
 * <b>cada</b> tool corta con error y no toca ningún otro colaborador. Cubre además el round-trip de
 * narrativa (guardar → aparece en ReportData/MD/PDF; sin narrativa el reporte sale igual). Llama a
 * {@link McpToolService} directo con la identidad del token (mismo camino que el adaptador
 * {@code @McpTool}), sin depender del protocolo MCP (eso lo cubre el e2e).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class McpToolScopeTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static final String STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("filgrama-mcp-it").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("storage.backend", () -> "local");
        registry.add("storage.local.base-dir", () -> STORAGE_DIR);
    }

    private static final LocalDate MAY_FROM = LocalDate.parse("2026-05-01");
    private static final LocalDate MAY_TO = LocalDate.parse("2026-05-31");

    private static boolean seeded;
    private static Long employeeId;
    private static Long clientAId;    // cliente cualquiera — el empleado NO tiene favorito acá
    private static Long clientBId;    // segundo cliente, mismo trato
    private static Long accountAId;

    @Autowired McpToolService tools;
    @Autowired ReportService reportService;
    @Autowired ReportRepository reports;
    @Autowired MarkdownRenderer markdownRenderer;
    @Autowired PdfRenderer pdfRenderer;
    @Autowired UserRepository users;
    @Autowired ClientRepository clients;
    @Autowired SocialAccountRepository accounts;
    @Autowired AccountMetricSnapshotRepository accountSnapshots;
    @Autowired PostRepository posts;
    @Autowired PostMetricSnapshotRepository postSnapshots;

    @BeforeEach
    void seed() {
        if (seeded) {
            return;
        }
        User employee = new User();
        employee.setEmail("empleado.mcp@filgrama.local");
        employee.setPasswordHash("x");
        employee.setFullName("Empleado MCP");
        employee.setRole(Role.EMPLEADO);
        employeeId = users.save(employee).getId();

        // Sin fila en employee_client_priority para este empleado: v1 no la usa como permiso
        // (spec/02), así que el empleado debe ver estos clientes igual que el admin.
        clientAId = clients.save(newClient("Oh My Bunny")).getId();
        clientBId = clients.save(newClient("Segundo Cliente")).getId();

        accountAId = newAccount(clientAId, "ig-a");
        Long accountBId = newAccount(clientBId, "ig-b");

        accountSnapshot(clientAId, accountAId, "ig_reach", "125400", "2026-05-15");
        accountSnapshot(clientAId, accountAId, "ig_followers_count", "5200", "2026-05-15");
        Long reel = newPost(clientAId, accountAId, PostType.REEL, "reel1",
                Instant.parse("2026-05-20T13:00:00Z"));
        postSnapshot(clientAId, accountAId, reel, "ig_post_reach", "42000", "2026-05-21");
        postSnapshot(clientAId, accountAId, reel, "ig_post_total_interactions", "3100", "2026-05-21");

        accountSnapshot(clientBId, accountBId, "ig_reach", "999999", "2026-05-15");

        seeded = true;
    }

    // ============================ list_clients ============================

    @Test
    void listClients_employeeSeesSameAsAdmin() {
        List<Long> employeeSeen = tools.listClients(employee()).stream().map(ClientView::id).toList();
        List<Long> adminSeen = tools.listClients(admin()).stream().map(ClientView::id).toList();
        assertThat(employeeSeen).containsExactlyInAnyOrderElementsOf(adminSeen);
        assertThat(employeeSeen).contains(clientAId, clientBId);
    }

    // ============================ paridad empleado/admin (regla v1) ============================

    @Test
    void readTools_employeeSeesSameAsAdmin() {
        McpIdentity emp = employee();
        McpIdentity adm = admin();

        assertThat(tools.getClientReportData(emp, clientAId, "2026-05"))
                .isEqualTo(tools.getClientReportData(adm, clientAId, "2026-05"));
        assertThat(tools.getAudienceDemographics(emp, clientAId, "2026-05"))
                .isEqualTo(tools.getAudienceDemographics(adm, clientAId, "2026-05"));
        assertThat(tools.comparePeriods(emp, clientAId, "2026-04", "2026-05"))
                .isEqualTo(tools.comparePeriods(adm, clientAId, "2026-04", "2026-05"));
        assertThat(tools.getPostingPerformance(emp, clientAId, "hour"))
                .isEqualTo(tools.getPostingPerformance(adm, clientAId, "hour"));
        // get_metric_series se scopea por la cuenta → el cliente al que pertenece.
        assertThat(tools.getMetricSeries(emp, accountAId, "ig_reach", null, null))
                .isEqualTo(tools.getMetricSeries(adm, accountAId, "ig_reach", null, null));

        // También para el segundo cliente, sin ningún favorito/asignación de por medio.
        assertThat(tools.getClientReportData(emp, clientBId, "2026-05"))
                .isEqualTo(tools.getClientReportData(adm, clientBId, "2026-05"));
    }

    // ============================ choke point (regla dura: se aplica en TODAS las tools) ============================

    @Test
    void chokePoint_appliesToEveryTool_whenAccessServiceDenies() {
        ClientAccessService denying = mock(ClientAccessService.class);
        ApiException denied = new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "fuera de scope (stub)");
        when(denying.listAccessible(any())).thenThrow(denied);
        when(denying.requireClient(any(), any())).thenThrow(denied);
        when(denying.requireAccount(any(), any())).thenThrow(denied);

        // Colaboradores mockeados: si el choke point falla en frenar antes de usarlos, la
        // verifyNoInteractions de abajo lo detecta (ningún dato debería llegar a tocarlos).
        ReportNarrativeService narrativeSpy = mock(ReportNarrativeService.class);
        McpToolService gated = new McpToolService(denying, mock(ReportService.class),
                mock(MetricReportService.class), mock(ReportQueryRepository.class), narrativeSpy);

        McpIdentity emp = employee();
        assertForbidden(() -> gated.listClients(emp));
        assertForbidden(() -> gated.getClientReportData(emp, clientAId, "2026-05"));
        assertForbidden(() -> gated.getAudienceDemographics(emp, clientAId, "2026-05"));
        assertForbidden(() -> gated.comparePeriods(emp, clientAId, "2026-04", "2026-05"));
        assertForbidden(() -> gated.getPostingPerformance(emp, clientAId, "hour"));
        assertForbidden(() -> gated.getMetricSeries(emp, accountAId, "ig_reach", null, null));
        assertForbidden(() -> gated.saveReportNarrative(
                emp, clientAId, "2026-05", "intento indebido", "claude-opus-4-8"));

        verifyNoInteractions(narrativeSpy);
    }

    // ============================ narrativa (guardar → aparece; sin narrativa, igual) ============================

    @Test
    void saveNarrative_appearsInReportDataAndRenderers() {
        String markdown = """
                Mayo fue un mes **muy bueno** para la marca.

                - El alcance creció con respecto a abril.
                - Los reels concentraron la atención.

                Recomendación: sostener la frecuencia de reels.""";

        NarrativeSaved saved = tools.saveReportNarrative(
                admin(), clientAId, "2026-05", markdown, "claude-opus-4-8");
        assertThat(saved.reportId()).isNotNull();
        assertThat(saved.from()).isEqualTo(MAY_FROM);
        assertThat(saved.to()).isEqualTo(MAY_TO);

        Report row = reports.findById(saved.reportId()).orElseThrow();
        assertThat(row.getNarrativeSource()).isEqualTo("MCP");
        assertThat(row.getNarrativeModel()).isEqualTo("claude-opus-4-8");
        assertThat(row.getNarrativeGeneratedAt()).isNotNull();

        // La vista (:preview) y el export salen del MISMO armado → ambos ven la narrativa.
        ReportData data = reportService.buildReportData(clientAId, ReportType.EXTENDED, null,
                MAY_FROM, MAY_TO, null, null, null);
        assertThat(data.hasNarrative()).isTrue();
        assertThat(data.narrativeMd()).isEqualTo(markdown.strip());

        assertThat(markdownRenderer.render(data)).contains("Análisis del mes").contains("muy bueno");
        assertThat(pdfRenderer.render(data)).isNotEmpty();
    }

    @Test
    void withoutNarrative_reportComesOutTheSame() {
        // El segundo cliente no tiene narrativa → narrativeMd null, sin sección "Análisis del mes".
        ReportData data = reportService.buildReportData(clientBId, ReportType.EXTENDED, null,
                MAY_FROM, MAY_TO, null, null, null);
        assertThat(data.hasNarrative()).isFalse();
        assertThat(markdownRenderer.render(data)).doesNotContain("Análisis del mes");
    }

    // ============================ helpers ============================

    private McpIdentity employee() {
        return new McpIdentity(employeeId, Role.EMPLEADO);
    }

    private McpIdentity admin() {
        User admin = users.findByEmail("admin@filgrama.local").orElseThrow();
        return new McpIdentity(admin.getId(), admin.getRole());
    }

    private static void assertForbidden(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Client newClient(String name) {
        Client c = new Client();
        c.setName(name);
        c.setTimezone("America/Asuncion");
        return c;
    }

    private Long newAccount(Long clientId, String ext) {
        SocialAccount a = new SocialAccount();
        a.setClientId(clientId);
        a.setPlatform(Platform.INSTAGRAM);
        a.setExternalAccountId(ext);
        a.setHandle("@" + ext);
        a.setConnectedAt(Instant.now());
        return accounts.save(a).getId();
    }

    private Long newPost(Long clientId, Long accountId, PostType type, String ext, Instant publishedAt) {
        Post p = new Post();
        p.setClientId(clientId);
        p.setAccountId(accountId);
        p.setPlatform(Platform.INSTAGRAM);
        p.setExternalPostId(ext);
        p.setPostType(type);
        p.setPermalink("https://instagram.com/p/" + ext);
        p.setCaption("Post " + ext);
        p.setPublishedAt(publishedAt);
        p.setFirstSeenAt(Instant.now());
        return posts.save(p).getId();
    }

    private void accountSnapshot(Long clientId, Long accountId, String metric, String value, String date) {
        AccountMetricSnapshot s = new AccountMetricSnapshot();
        s.setClientId(clientId);
        s.setAccountId(accountId);
        s.setMetricKey(metric);
        s.setValue(new BigDecimal(value));
        LocalDate d = LocalDate.parse(date);
        s.setCapturedAt(d.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        s.setCaptureDate(d);
        accountSnapshots.save(s);
    }

    private void postSnapshot(Long clientId, Long accountId, Long postId, String metric, String value, String date) {
        PostMetricSnapshot s = new PostMetricSnapshot();
        s.setClientId(clientId);
        s.setAccountId(accountId);
        s.setPostId(postId);
        s.setMetricKey(metric);
        s.setValue(new BigDecimal(value));
        LocalDate d = LocalDate.parse(date);
        s.setCapturedAt(d.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        s.setCaptureDate(d);
        postSnapshots.save(s);
    }
}
