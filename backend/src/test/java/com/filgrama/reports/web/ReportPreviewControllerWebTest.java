package com.filgrama.reports.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.error.ApiException;
import com.filgrama.error.GlobalExceptionHandler;
import com.filgrama.reports.ReportService;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.Highlights;

/**
 * Capa web de {@code POST /api/v1/clients/{clientId}/reports:preview} (custom method AIP-136) vía
 * MockMvc standalone. Verifica que el {@code PathPattern} matchea el literal {@code reports:preview},
 * que la response es el {@link ReportData} serializado (mismos números que el export) y el mapeo de
 * errores a problem+json. No genera ni persiste archivo.
 */
class ReportPreviewControllerWebTest {

    private ReportService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReportPreviewController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ReportData sampleData() {
        Highlights empty = new Highlights(List.of(), List.of());
        return new ReportData(ReportType.SUMMARY, null,
                new ReportData.Client(9L, "Molinos", "America/Asuncion", "Pro"),
                new ReportData.Period(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"),
                        LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-30")),
                List.of("INSTAGRAM"), "reach",
                List.of(), List.of(), List.of(), List.of(), empty, empty, null);
    }

    private static final String BODY = """
            { "reportType":"SUMMARY", "from":"2026-05-01", "to":"2026-05-31",
              "platforms":["INSTAGRAM"], "rankBy":"reach" }
            """;

    @Test
    void previewMatchesColonRouteAndReturnsReportDataJson() throws Exception {
        when(service.preview(eq(9L), any(PreviewReportRequest.class))).thenReturn(sampleData());

        mvc.perform(post("/api/v1/clients/9/reports:preview")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportType").value("SUMMARY"))
                .andExpect(jsonPath("$.client.name").value("Molinos"))
                .andExpect(jsonPath("$.period.from").value("2026-05-01"))
                .andExpect(jsonPath("$.period.to").value("2026-05-31"))
                .andExpect(jsonPath("$.platforms[0]").value("INSTAGRAM"))
                .andExpect(jsonPath("$.rankBy").value("reach"));
    }

    @Test
    void previewMissingRequiredFieldIsBadRequest() throws Exception {
        // Falta 'reportType' → @NotNull → 400 vía el handler.
        String invalid = """
                { "from":"2026-05-01", "to":"2026-05-31", "platforms":["INSTAGRAM"] }
                """;

        mvc.perform(post("/api/v1/clients/9/reports:preview")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewUnknownClientIsNotFoundProblemJson() throws Exception {
        when(service.preview(eq(9L), any(PreviewReportRequest.class)))
                .thenThrow(ApiException.notFound("Client 9 not found"));

        mvc.perform(post("/api/v1/clients/9/reports:preview")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
