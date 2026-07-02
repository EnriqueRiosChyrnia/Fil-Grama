package com.filgrama.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.filgrama.domain.EmployeeClientPriority;
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
import com.filgrama.reports.Report;
import com.filgrama.reports.ReportRepository;
import com.filgrama.reports.ReportService;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.render.MarkdownRenderer;
import com.filgrama.reports.render.PdfRenderer;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.EmployeeClientPriorityRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;
import com.filgrama.repository.UserRepository;

/**
 * Aislamiento de scope de las 7 tools MCP contra un Postgres real (spec/08 §Decisiones T5, spec/11).
 * Regla dura: un EMPLEADO nunca ve un cliente/cuenta que no tiene asignado — <b>por ninguna tool</b>.
 * Cubre además el round-trip de narrativa (guardar → aparece en ReportData/MD/PDF; sin narrativa el
 * reporte sale igual). Llama a {@link McpToolService} directo con la identidad del token (mismo camino
 * que el adaptador {@code @McpTool}), sin depender del protocolo MCP (eso lo cubre el e2e).
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
    private static Long assignedClientId;   // asignado al empleado
    private static Long foreignClientId;    // NO asignado
    private static Long foreignAccountId;   // cuenta del cliente ajeno

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
    @Autowired EmployeeClientPriorityRepository assignments;

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

        assignedClientId = clients.save(newClient("Oh My Bunny")).getId();
        foreignClientId = clients.save(newClient("Cliente Ajeno")).getId();

        // Asignación del empleado SOLO al cliente asignado (fuente de scope del MCP en v1).
        EmployeeClientPriority link = new EmployeeClientPriority();
        link.setUserId(employeeId);
        link.setClientId(assignedClientId);
        link.setCreatedAt(Instant.now());
        assignments.save(link);

        Long assignedAccount = newAccount(assignedClientId, "ig-assigned");
        foreignAccountId = newAccount(foreignClientId, "ig-foreign");

        // Datos del cliente asignado (para el reporte + posting performance).
        accountSnapshot(assignedClientId, assignedAccount, "ig_reach", "125400", "2026-05-15");
        accountSnapshot(assignedClientId, assignedAccount, "ig_followers_count", "5200", "2026-05-15");
        Long reel = newPost(assignedClientId, assignedAccount, PostType.REEL, "reel1",
                Instant.parse("2026-05-20T13:00:00Z"));
        postSnapshot(assignedClientId, assignedAccount, reel, "ig_post_reach", "42000", "2026-05-21");
        postSnapshot(assignedClientId, assignedAccount, reel, "ig_post_total_interactions", "3100", "2026-05-21");

        // Dato en el cliente ajeno: si el scope fallara, se filtraría.
        accountSnapshot(foreignClientId, foreignAccountId, "ig_reach", "999999", "2026-05-15");

        seeded = true;
    }

    // ============================ list_clients ============================

    @Test
    void listClients_employeeSeesOnlyAssigned() {
        List<ClientView> visible = tools.listClients(employee());
        assertThat(visible).extracting(ClientView::id).containsExactly(assignedClientId);
        assertThat(visible).extracting(ClientView::id).doesNotContain(foreignClientId);
    }

    @Test
    void listClients_adminSeesAll() {
        assertThat(tools.listClients(admin()).stream().map(ClientView::id).toList())
                .contains(assignedClientId, foreignClientId);
    }

    // ============================ scope por tool (regla dura) ============================

    @Test
    void everyReadTool_deniesEmployeeOnForeignClientOrAccount() {
        McpIdentity emp = employee();
        assertForbidden(() -> tools.getClientReportData(emp, foreignClientId, "2026-05"));
        assertForbidden(() -> tools.getAudienceDemographics(emp, foreignClientId, "2026-05"));
        assertForbidden(() -> tools.comparePeriods(emp, foreignClientId, "2026-04", "2026-05"));
        assertForbidden(() -> tools.getPostingPerformance(emp, foreignClientId, "hour"));
        // get_metric_series se scopea por la cuenta → el cliente al que pertenece.
        assertForbidden(() -> tools.getMetricSeries(emp, foreignAccountId, "ig_reach", null, null));
    }

    @Test
    void saveReportNarrative_deniedForForeignClient_andNothingPersisted() {
        assertForbidden(() -> tools.saveReportNarrative(
                employee(), foreignClientId, "2026-05", "intento indebido", "claude-opus-4-8"));
        assertThat(reports.findFirstByClientIdAndPeriodFromAndPeriodToOrderByCreatedAtDesc(
                foreignClientId, MAY_FROM, MAY_TO)).isEmpty();
    }

    @Test
    void employeeCanAccessAssignedClient() {
        assertThat(tools.getClientReportData(employee(), assignedClientId, "2026-05")).isNotNull();
        assertThat(tools.getPostingPerformance(employee(), assignedClientId, "weekday")).isNotNull();
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
                admin(), assignedClientId, "2026-05", markdown, "claude-opus-4-8");
        assertThat(saved.reportId()).isNotNull();
        assertThat(saved.from()).isEqualTo(MAY_FROM);
        assertThat(saved.to()).isEqualTo(MAY_TO);

        Report row = reports.findById(saved.reportId()).orElseThrow();
        assertThat(row.getNarrativeSource()).isEqualTo("MCP");
        assertThat(row.getNarrativeModel()).isEqualTo("claude-opus-4-8");
        assertThat(row.getNarrativeGeneratedAt()).isNotNull();

        // La vista (:preview) y el export salen del MISMO armado → ambos ven la narrativa.
        ReportData data = reportService.buildReportData(assignedClientId, ReportType.EXTENDED, null,
                MAY_FROM, MAY_TO, null, null, null);
        assertThat(data.hasNarrative()).isTrue();
        assertThat(data.narrativeMd()).isEqualTo(markdown.strip());

        assertThat(markdownRenderer.render(data)).contains("Análisis del mes").contains("muy bueno");
        assertThat(pdfRenderer.render(data)).isNotEmpty();
    }

    @Test
    void withoutNarrative_reportComesOutTheSame() {
        // El cliente ajeno no tiene narrativa → narrativeMd null, sin sección "Análisis del mes".
        ReportData data = reportService.buildReportData(foreignClientId, ReportType.EXTENDED, null,
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
