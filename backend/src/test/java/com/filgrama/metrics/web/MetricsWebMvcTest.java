package com.filgrama.metrics.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.error.ApiException;
import com.filgrama.error.GlobalExceptionHandler;
import com.filgrama.metrics.dto.AccountReportResponse;
import com.filgrama.metrics.dto.BatchReportResponse;
import com.filgrama.metrics.dto.DateRange;
import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.metrics.dto.MetricSeries;
import com.filgrama.metrics.dto.PostReportResponse;
import com.filgrama.metrics.dto.SeriesPoint;
import com.filgrama.metrics.service.AccountPostsService;
import com.filgrama.metrics.service.MetricCatalogService;
import com.filgrama.metrics.service.MetricReportService;
import com.filgrama.metrics.service.SummaryService;

/**
 * Tests de la capa web vía MockMvc standalone (sólo spring-test, sin levantar contexto ni DB).
 * Verifica que el {@code PathPattern} matchea los custom methods {@code :report}/{@code :batchReport}
 * (AIP-136), el shape del contrato y el mapeo de errores a problem+json (vía {@link GlobalExceptionHandler}).
 */
class MetricsWebMvcTest {

    private static final DateRange RANGE = new DateRange(LocalDate.parse("2026-03-24"), LocalDate.parse("2026-06-22"));

    private MetricCatalogService catalogService;
    private MetricReportService reportService;
    private AccountPostsService postsService;
    private SummaryService summaryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        catalogService = mock(MetricCatalogService.class);
        reportService = mock(MetricReportService.class);
        postsService = mock(AccountPostsService.class);
        summaryService = mock(SummaryService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new MetricCatalogController(catalogService),
                        new AccountMetricsController(reportService, postsService),
                        new ClientSummaryController(summaryService),
                        new PostMetricsController(reportService),
                        new MetricsBatchController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void metricsCatalogReturnsJsonAndForwardsFilters() throws Exception {
        when(catalogService.list("INSTAGRAM", "ACCOUNT")).thenReturn(List.of(
                new MetricCatalogItem("ig_reach", "Alcance", "INSTAGRAM", "ACCOUNT", "count", "CORE", "ACTIVE")));

        mvc.perform(get("/api/v1/metrics").param("platform", "INSTAGRAM").param("level", "ACCOUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("ig_reach"))
                .andExpect(jsonPath("$[0].tier").value("CORE"));
    }

    @Test
    void accountReportMatchesColonRouteAndReturnsContractShape() throws Exception {
        when(reportService.accountReport(eq(7L), any())).thenReturn(new AccountReportResponse(7L, RANGE, "day", List.of(
                new MetricSeries("ig_reach", "count", List.of(
                        new SeriesPoint(LocalDate.parse("2026-06-01"), new BigDecimal("12450")),
                        new SeriesPoint(LocalDate.parse("2026-06-02"), new BigDecimal("13010")))),
                new MetricSeries("ig_followers_count", "count", List.of()))));

        mvc.perform(post("/api/v1/accounts/7/metrics:report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[\"ig_reach\",\"ig_followers_count\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.dateRange.from").value("2026-03-24"))
                .andExpect(jsonPath("$.series[0].metric").value("ig_reach"))
                .andExpect(jsonPath("$.series[0].unit").value("count"))
                .andExpect(jsonPath("$.series[0].points[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.series[0].points[1].value").value(13010))
                .andExpect(jsonPath("$.series[1].points").isEmpty());
    }

    @Test
    void invalidMetricIsBadRequestProblemJson() throws Exception {
        when(reportService.accountReport(eq(7L), any()))
                .thenThrow(ApiException.badRequest("metric_key 'bogus' no existe en el catálogo"));

        mvc.perform(post("/api/v1/accounts/7/metrics:report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[\"bogus\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("metric_key 'bogus' no existe en el catálogo"));
    }

    @Test
    void missingAccountIsNotFound() throws Exception {
        when(reportService.accountReport(eq(99L), any())).thenThrow(ApiException.notFound("Account 99 not found"));

        mvc.perform(post("/api/v1/accounts/99/metrics:report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[\"ig_reach\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void postReportMatchesColonRouteAndReturnsContractShape() throws Exception {
        when(reportService.postReport(eq(5L), any())).thenReturn(new PostReportResponse(5L, RANGE, "day", List.of(
                new MetricSeries("ig_post_likes", "count", List.of(
                        new SeriesPoint(LocalDate.parse("2026-06-01"), new BigDecimal("42")))))));

        mvc.perform(post("/api/v1/posts/5/metrics:report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[\"ig_post_likes\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(5))
                .andExpect(jsonPath("$.series[0].points[0].value").value(42));
    }

    @Test
    void batchReportMatchesColonRouteAndPreservesOrder() throws Exception {
        when(reportService.batchReport(any())).thenReturn(new BatchReportResponse(List.of(
                new AccountReportResponse(7L, RANGE, "day", List.of()),
                new AccountReportResponse(12L, RANGE, "day", List.of()))));

        mvc.perform(post("/api/v1/metrics:batchReport")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requests\":[{\"accountId\":7,\"metrics\":[\"ig_reach\"]},"
                                + "{\"accountId\":12,\"metrics\":[\"tt_view_count\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].accountId").value(7))
                .andExpect(jsonPath("$.reports[1].accountId").value(12));
    }

    @Test
    void batchOverLimitIsBadRequest() throws Exception {
        when(reportService.batchReport(any()))
                .thenThrow(ApiException.badRequest("el batch excede el máximo de 20 requests (recibidos 21)"));

        mvc.perform(post("/api/v1/metrics:batchReport")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requests\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void summaryUnknownClientIsNotFound() throws Exception {
        when(summaryService.summary(eq(42L), any(), any(), any()))
                .thenThrow(ApiException.notFound("Client 42 not found"));

        mvc.perform(get("/api/v1/clients/42/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
