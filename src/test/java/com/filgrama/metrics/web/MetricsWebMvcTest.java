package com.filgrama.metrics.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.error.ApiException;
import com.filgrama.error.GlobalExceptionHandler;
import com.filgrama.metrics.dto.AccountSeriesResponse;
import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.metrics.dto.PostSeriesResponse;
import com.filgrama.metrics.dto.SeriesPoint;
import com.filgrama.metrics.service.AccountPostsService;
import com.filgrama.metrics.service.MetricCatalogService;
import com.filgrama.metrics.service.MetricSeriesService;
import com.filgrama.metrics.service.SummaryService;

/**
 * Tests de la capa web vía MockMvc standalone (sólo spring-test, sin levantar contexto ni DB).
 * Registra el {@link GlobalExceptionHandler} compartido para verificar el mapeo a problem+json.
 */
class MetricsWebMvcTest {

    private MetricCatalogService catalogService;
    private MetricSeriesService seriesService;
    private AccountPostsService postsService;
    private SummaryService summaryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        catalogService = mock(MetricCatalogService.class);
        seriesService = mock(MetricSeriesService.class);
        postsService = mock(AccountPostsService.class);
        summaryService = mock(SummaryService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new MetricCatalogController(catalogService),
                        new AccountMetricsController(seriesService, postsService),
                        new ClientSummaryController(summaryService),
                        new PostMetricsController(seriesService))
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
                .andExpect(jsonPath("$[0].displayName").value("Alcance"))
                .andExpect(jsonPath("$[0].tier").value("CORE"));
    }

    @Test
    void accountSeriesReturnsContractShape() throws Exception {
        when(seriesService.accountSeries(eq(7L), eq("ig_reach"), any(), any(), any()))
                .thenReturn(new AccountSeriesResponse(7L, "ig_reach", "day", List.of(
                        new SeriesPoint(Instant.parse("2026-06-01T03:00:00Z"), new BigDecimal("12450")),
                        new SeriesPoint(Instant.parse("2026-06-02T03:00:00Z"), new BigDecimal("13010")))));

        mvc.perform(get("/api/v1/accounts/7/metrics").param("metric", "ig_reach"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.metric").value("ig_reach"))
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.points[0].value").value(12450))
                .andExpect(jsonPath("$.points[1].value").value(13010));
    }

    @Test
    void missingMetricParamIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/accounts/7/metrics"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownMetricIsUnprocessableProblemJson() throws Exception {
        when(seriesService.accountSeries(eq(7L), eq("bogus"), any(), any(), any()))
                .thenThrow(ApiException.unprocessable("metric_key 'bogus' no existe en el catálogo"));

        mvc.perform(get("/api/v1/accounts/7/metrics").param("metric", "bogus"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("metric_key 'bogus' no existe en el catálogo"));
    }

    @Test
    void summaryUnknownClientIsNotFound() throws Exception {
        when(summaryService.summary(eq(42L), any(), any(), any()))
                .thenThrow(ApiException.notFound("Client 42 not found"));

        mvc.perform(get("/api/v1/clients/42/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void postSeriesReturnsContractShape() throws Exception {
        when(seriesService.postSeries(eq(5L), eq("ig_post_likes"), any(), any()))
                .thenReturn(new PostSeriesResponse(5L, "ig_post_likes", List.of(
                        new SeriesPoint(Instant.parse("2026-06-01T03:00:00Z"), new BigDecimal("42")))));

        mvc.perform(get("/api/v1/posts/5/metrics").param("metric", "ig_post_likes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(5))
                .andExpect(jsonPath("$.points[0].value").value(42));
    }
}
