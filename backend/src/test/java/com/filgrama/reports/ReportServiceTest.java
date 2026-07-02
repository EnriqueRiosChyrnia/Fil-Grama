package com.filgrama.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.filgrama.domain.Client;
import com.filgrama.error.ApiException;
import com.filgrama.reports.ReportService.DownloadPayload;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportDataAssembler;
import com.filgrama.reports.render.MarkdownRenderer;
import com.filgrama.reports.render.PdfRenderer;
import com.filgrama.reports.web.GenerateReportRequest;
import com.filgrama.reports.web.PreviewReportRequest;
import com.filgrama.repository.ClientRepository;
import com.filgrama.storage.StoragePort;
import com.filgrama.storage.StoredObject;

/** Orquestación del reporte con dependencias mockeadas: persistencia, storage y multi-tenant. */
class ReportServiceTest {

    private static final Long CLIENT = 9L;
    private static final LocalDate FROM = LocalDate.parse("2026-05-01");
    private static final LocalDate TO = LocalDate.parse("2026-05-31");

    private ReportRepository repo;
    private ReportDataAssembler assembler;
    private ReportNarrativeService narrativeService;
    private MarkdownRenderer markdown;
    private PdfRenderer pdf;
    private StoragePort storage;
    private ClientRepository clients;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repo = mock(ReportRepository.class);
        assembler = mock(ReportDataAssembler.class);
        narrativeService = mock(ReportNarrativeService.class);
        markdown = mock(MarkdownRenderer.class);
        pdf = mock(PdfRenderer.class);
        storage = mock(StoragePort.class);
        clients = mock(ClientRepository.class);
        // Sin narrativa persistida (comportamiento por defecto): el reporte sale igual que hoy.
        when(narrativeService.findNarrative(any(), any(), any())).thenReturn(null);
        service = new ReportService(repo, assembler, narrativeService, markdown, pdf, storage, clients);

        // save() asigna id la primera vez y devuelve la misma entidad (como Spring Data).
        when(repo.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(55L);
            }
            return r;
        });
    }

    private ReportData data(ReportType type, ReportFormat format) {
        Highlights empty = new Highlights(List.of(), List.of());
        return new ReportData(type, format,
                new ReportData.Client(CLIENT, "Molinos", "UTC", null),
                new ReportData.Period(FROM, TO, FROM.minusMonths(1), TO.minusMonths(1)),
                List.of("INSTAGRAM"), "reach", List.of(), List.of(), List.of(), List.of(),
                empty, empty, null);
    }

    @Test
    void generateSummaryMarkdownStoresFileAndMarksCompleted() {
        GenerateReportRequest req = new GenerateReportRequest(
                ReportType.SUMMARY, ReportFormat.MARKDOWN, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        when(assembler.assemble(eq(CLIENT), eq(ReportType.SUMMARY), eq(ReportFormat.MARKDOWN),
                eq(FROM), eq(TO), any(), any(), eq("reach")))
                .thenReturn(data(ReportType.SUMMARY, ReportFormat.MARKDOWN));
        when(markdown.render(any())).thenReturn("# Reporte\n");
        when(storage.put(anyString(), any(), anyString()))
                .thenAnswer(inv -> new StoredObject(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        Report report = service.generate(CLIENT, req, 3L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(report.getCreatedBy()).isEqualTo(3L);
        assertThat(report.getStoragePath()).isEqualTo("reports/9/55.md");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(key.capture(), body.capture(), eq("text/markdown"));
        assertThat(key.getValue()).isEqualTo("reports/9/55.md");
        assertThat(new String(body.getValue(), StandardCharsets.UTF_8)).isEqualTo("# Reporte\n");
        verify(pdf, never()).render(any());
    }

    @Test
    void generateMarksFailedAndRethrowsWhenRenderFails() {
        GenerateReportRequest req = new GenerateReportRequest(
                ReportType.EXTENDED, ReportFormat.PDF, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        when(assembler.assemble(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(data(ReportType.EXTENDED, ReportFormat.PDF));
        when(pdf.render(any())).thenThrow(ApiException.unprocessable("boom"));

        assertThatThrownBy(() -> service.generate(CLIENT, req, 3L))
                .isInstanceOf(ApiException.class);

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(repo, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.FAILED);
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void assembleRuntimeFailureIsMappedToApiExceptionNeverRaw500() {
        // assemble() corre ANTES del try/catch: una RuntimeException que NO sea ApiException (p. ej.
        // storage caído al resolver una miniatura) escapaba como 500 crudo. Debe mapear a ApiException.
        GenerateReportRequest req = new GenerateReportRequest(
                ReportType.SUMMARY, ReportFormat.PDF, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        when(assembler.assemble(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("MinIO unreachable: Connection refused"));

        assertThatThrownBy(() -> service.generate(CLIENT, req, 3L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(422));

        // No deja un reporte fantasma: si no se pudo armar el ReportData, no se persiste fila.
        verify(repo, never()).save(any(Report.class));
    }

    @Test
    void assembleApiExceptionIsPropagatedUnchanged() {
        // Las validaciones de assemble (404 cliente, 400 rango, 422 rankBy) deben pasar tal cual.
        GenerateReportRequest req = new GenerateReportRequest(
                ReportType.SUMMARY, ReportFormat.PDF, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        when(assembler.assemble(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(ApiException.notFound("Client %d not found".formatted(CLIENT)));

        assertThatThrownBy(() -> service.generate(CLIENT, req, 3L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));

        verify(repo, never()).save(any(Report.class));
    }

    // ---- :preview (vista en pantalla = export: mismo ReportData, sin archivo) ----

    @Test
    void previewReturnsAssembledDataWithoutPersisting() {
        PreviewReportRequest req = new PreviewReportRequest(
                ReportType.SUMMARY, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        ReportData expected = data(ReportType.SUMMARY, null);
        // preview no exporta: no hay format. assemble se invoca con format == null.
        when(assembler.assemble(eq(CLIENT), eq(ReportType.SUMMARY), isNull(),
                eq(FROM), eq(TO), any(), any(), eq("reach"))).thenReturn(expected);

        ReportData result = service.preview(CLIENT, req);

        assertThat(result).isSameAs(expected);
        verify(repo, never()).save(any(Report.class)); // no persiste fila ni archivo
        verify(markdown, never()).render(any());
        verify(pdf, never()).render(any());
    }

    @Test
    void previewPropagatesValidationApiException() {
        PreviewReportRequest req = new PreviewReportRequest(
                ReportType.SUMMARY, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        when(assembler.assemble(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(ApiException.notFound("Client %d not found".formatted(CLIENT)));

        assertThatThrownBy(() -> service.preview(CLIENT, req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    void previewMapsUnexpectedFailureToApiExceptionNeverRaw500() {
        PreviewReportRequest req = new PreviewReportRequest(
                ReportType.SUMMARY, FROM, TO, List.of("INSTAGRAM"), null, "reach");
        when(assembler.assemble(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("MinIO unreachable"));

        assertThatThrownBy(() -> service.preview(CLIENT, req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(422));
    }

    @Test
    void getOfAnotherClientIsNotFound() {
        when(repo.findByIdAndClientId(77L, CLIENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CLIENT, 77L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    void downloadReturnsBytesWithContentTypeAndFilename() {
        Report report = new Report();
        report.setId(55L);
        report.setClientId(CLIENT);
        report.setReportType(ReportType.SUMMARY);
        report.setFormat(ReportFormat.PDF);
        report.setStatus(ReportStatus.COMPLETED);
        report.setPeriodFrom(LocalDate.parse("2026-03-27"));
        report.setPeriodTo(LocalDate.parse("2026-06-24"));
        report.setStoragePath("reports/9/55.pdf");
        when(repo.findByIdAndClientId(55L, CLIENT)).thenReturn(Optional.of(report));
        when(storage.get("reports/9/55.pdf")).thenReturn("%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1));
        when(clients.findById(CLIENT)).thenReturn(Optional.of(client("TikTok Prueba")));

        DownloadPayload payload = service.download(CLIENT, 55L);

        assertThat(payload.contentType()).isEqualTo("application/pdf");
        // Filename con slug del cliente + tipo + rango (el título DENTRO del reporte ya trae el nombre).
        assertThat(payload.filename())
                .isEqualTo("reporte-tiktok-prueba-summary-2026-03-27_a_2026-06-24.pdf");
        assertThat(payload.content()).isNotEmpty();
    }

    @Test
    void downloadFilenameSlugStripsAccentsAndSymbols() {
        Report report = new Report();
        report.setId(7L);
        report.setClientId(CLIENT);
        report.setReportType(ReportType.EXTENDED);
        report.setFormat(ReportFormat.MARKDOWN);
        report.setStatus(ReportStatus.COMPLETED);
        report.setPeriodFrom(LocalDate.parse("2026-05-01"));
        report.setPeriodTo(LocalDate.parse("2026-05-31"));
        report.setStoragePath("reports/9/7.md");
        when(repo.findByIdAndClientId(7L, CLIENT)).thenReturn(Optional.of(report));
        when(storage.get("reports/9/7.md")).thenReturn("# Reporte".getBytes(StandardCharsets.UTF_8));
        when(clients.findById(CLIENT)).thenReturn(Optional.of(client("Café & Té  São Paulo!")));

        assertThat(service.download(CLIENT, 7L).filename())
                .isEqualTo("reporte-cafe-te-sao-paulo-extended-2026-05-01_a_2026-05-31.md");
    }

    private static Client client(String name) {
        Client c = new Client();
        c.setId(CLIENT);
        c.setName(name);
        return c;
    }
}
