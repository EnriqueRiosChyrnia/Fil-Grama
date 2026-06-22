package com.filgrama.reports.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.error.ApiException;
import com.filgrama.error.GlobalExceptionHandler;
import com.filgrama.reports.Report;
import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportService;
import com.filgrama.reports.ReportService.DownloadPayload;
import com.filgrama.reports.ReportStatus;
import com.filgrama.reports.ReportType;

/**
 * Capa web de reportes vía MockMvc standalone (sólo spring-test, sin contexto ni DB). Registra el
 * handler compartido (problem+json) y el resolver de {@code @AuthenticationPrincipal}.
 */
class ReportControllerWebTest {

    private ReportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportService.class);
        // Defaults de standalone: Jackson (con jsr310), ByteArray converter y validador @Valid.
        mvc = MockMvcBuilders.standaloneSetup(new ReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private Report completed(ReportType type, ReportFormat format) {
        Report r = new Report();
        r.setId(55L);
        r.setClientId(9L);
        r.setReportType(type);
        r.setFormat(format);
        r.setStatus(ReportStatus.COMPLETED);
        r.setStoragePath("reports/9/55." + format.extension());
        try {
            var f = Report.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(r, Instant.parse("2026-06-01T10:00:00Z"));
        } catch (ReflectiveOperationException ignored) {
            // createdAt lo setea Hibernate en runtime; en el test lo forzamos por reflexión.
        }
        return r;
    }

    @Test
    void postCreatesReportAndReturns201Resource() throws Exception {
        when(service.generate(eq(9L), any(GenerateReportRequest.class), nullable(Long.class)))
                .thenReturn(completed(ReportType.EXTENDED, ReportFormat.PDF));

        String body = """
                { "reportType":"EXTENDED", "format":"PDF", "from":"2026-05-01", "to":"2026-05-31",
                  "platforms":["INSTAGRAM","TIKTOK"], "rankBy":"reach" }
                """;

        mvc.perform(post("/api/v1/clients/9/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.reportType").value("EXTENDED"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/clients/9/reports/55/download"));
    }

    @Test
    void missingRequiredFieldIsBadRequest() throws Exception {
        // Falta 'format' → @NotNull → 400 vía el handler.
        String body = """
                { "reportType":"SUMMARY", "from":"2026-05-01", "to":"2026-05-31" }
                """;

        mvc.perform(post("/api/v1/clients/9/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMetadataReturnsResource() throws Exception {
        when(service.get(9L, 55L)).thenReturn(completed(ReportType.SUMMARY, ReportFormat.MARKDOWN));

        mvc.perform(get("/api/v1/clients/9/reports/55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.format").value("MARKDOWN"));
    }

    @Test
    void getUnknownReportIsNotFoundProblemJson() throws Exception {
        when(service.get(9L, 999L)).thenThrow(ApiException.notFound("Report 999 not found for client 9"));

        mvc.perform(get("/api/v1/clients/9/reports/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void downloadSetsContentTypeAndDisposition() throws Exception {
        when(service.download(9L, 55L)).thenReturn(new DownloadPayload(
                "%PDF-1.7 binary".getBytes(StandardCharsets.ISO_8859_1), "application/pdf", "reporte-55.pdf"));

        mvc.perform(get("/api/v1/clients/9/reports/55/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("reporte-55.pdf")));
    }
}
