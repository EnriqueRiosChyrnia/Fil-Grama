package com.filgrama.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.Metric;
import com.filgrama.domain.Post;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.AccountReportRequest;
import com.filgrama.metrics.dto.AccountReportResponse;
import com.filgrama.metrics.dto.BatchReportRequest;
import com.filgrama.metrics.dto.BatchReportResponse;
import com.filgrama.metrics.dto.DateRange;
import com.filgrama.metrics.dto.MetricsReportRequest;
import com.filgrama.metrics.repository.MetricsQueryRepository;
import com.filgrama.metrics.repository.MetricsQueryRepository.SeriesRow;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;

class MetricReportServiceTest {

    // Hoy fijo para testear el default de 90 días de forma determinista.
    private static final LocalDate TODAY = LocalDate.parse("2026-06-22");
    private static final Clock FIXED = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private MetricCatalogService catalog;
    private SocialAccountRepository accountRepository;
    private PostRepository postRepository;
    private MetricsQueryRepository queryRepository;
    private MetricReportService service;

    @BeforeEach
    void setUp() {
        catalog = mock(MetricCatalogService.class);
        accountRepository = mock(SocialAccountRepository.class);
        postRepository = mock(PostRepository.class);
        queryRepository = mock(MetricsQueryRepository.class);
        service = new MetricReportService(catalog, accountRepository, postRepository, queryRepository, FIXED);
    }

    @Test
    void accountReportReturnsOneSeriesPerMetricInOrderWithUnit() {
        stubMetrics("ig_reach", "ig_followers_count");
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(queryRepository.accountMetricSeries(eq(1L), eq(7L), any(), any(), any()))
                .thenReturn(List.of(
                        new SeriesRow("ig_reach", LocalDate.parse("2026-06-01"), new BigDecimal("12450.0000")),
                        new SeriesRow("ig_reach", LocalDate.parse("2026-06-02"), new BigDecimal("13010")),
                        new SeriesRow("ig_followers_count", LocalDate.parse("2026-06-01"), new BigDecimal("980"))));

        AccountReportResponse res = service.accountReport(7L,
                new MetricsReportRequest(List.of("ig_reach", "ig_followers_count"), null, null));

        assertThat(res.accountId()).isEqualTo(7L);
        assertThat(res.granularity()).isEqualTo("day");
        assertThat(res.series()).extracting(s -> s.metric()).containsExactly("ig_reach", "ig_followers_count");
        assertThat(res.series().get(0).unit()).isEqualTo("count");
        assertThat(res.series().get(0).points()).extracting(p -> p.value().toPlainString())
                .containsExactly("12450", "13010"); // limpiado y en orden de captura
        assertThat(res.series().get(0).points().get(0).date()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(res.series().get(1).points()).hasSize(1);
    }

    @Test
    void accountReportDefaultsToLast90DaysWhenNoRange() {
        stubMetrics("ig_reach");
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(queryRepository.accountMetricSeries(anyLong(), anyLong(), any(), any(), any())).thenReturn(List.of());

        AccountReportResponse res = service.accountReport(7L, new MetricsReportRequest(List.of("ig_reach"), null, null));

        // hoy-89 .. hoy
        assertThat(res.dateRange()).isEqualTo(new DateRange(LocalDate.parse("2026-03-25"), TODAY));
        verify(queryRepository).accountMetricSeries(1L, 7L, java.util.Set.of("ig_reach"),
                LocalDate.parse("2026-03-25"), TODAY);
    }

    @Test
    void emptyRangeReturnsSeriesWithEmptyPointsNotError() {
        stubMetrics("ig_reach");
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(queryRepository.accountMetricSeries(anyLong(), anyLong(), any(), any(), any())).thenReturn(List.of());

        AccountReportResponse res = service.accountReport(7L, new MetricsReportRequest(
                List.of("ig_reach"), new DateRange(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30")), "day"));

        assertThat(res.series()).hasSize(1);
        assertThat(res.series().get(0).points()).isEmpty();
    }

    @Test
    void invalidMetricIsBadRequest() {
        when(catalog.requireReportMetrics(any())).thenThrow(ApiException.badRequest("metric_key 'bogus' no existe"));
        assertThatThrownBy(() -> service.accountReport(7L, new MetricsReportRequest(List.of("bogus"), null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
        verify(accountRepository, never()).findById(anyLong());
    }

    @Test
    void fromAfterToIsBadRequest() {
        stubMetrics("ig_reach");
        assertThatThrownBy(() -> service.accountReport(7L, new MetricsReportRequest(
                List.of("ig_reach"), new DateRange(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-01")), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void unsupportedGranularityIsBadRequest() {
        stubMetrics("ig_reach");
        assertThatThrownBy(() -> service.accountReport(7L,
                new MetricsReportRequest(List.of("ig_reach"), null, "week")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void missingAccountIsNotFound() {
        stubMetrics("ig_reach");
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accountReport(99L, new MetricsReportRequest(List.of("ig_reach"), null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(404));
    }

    @Test
    void postReportUsesPostIdAndClientScopedQuery() {
        stubMetrics("ig_post_likes");
        when(postRepository.findById(5L)).thenReturn(Optional.of(post(5L, 3L)));
        when(queryRepository.postMetricSeries(eq(3L), eq(5L), any(), any(), any()))
                .thenReturn(List.of(new SeriesRow("ig_post_likes", LocalDate.parse("2026-06-01"), new BigDecimal("42"))));

        var res = service.postReport(5L, new MetricsReportRequest(List.of("ig_post_likes"), null, null));

        assertThat(res.postId()).isEqualTo(5L);
        assertThat(res.series().get(0).points().get(0).value().toPlainString()).isEqualTo("42");
    }

    // ---- batch ----

    @Test
    void batchPreservesRequestOrderAndIsolatesTenants() {
        stubMetrics("ig_reach");
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));   // cliente 1
        when(accountRepository.findById(12L)).thenReturn(Optional.of(account(12L, 2L))); // cliente 2
        when(queryRepository.accountMetricSeries(anyLong(), anyLong(), any(), any(), any())).thenReturn(List.of());

        BatchReportResponse res = service.batchReport(new BatchReportRequest(List.of(
                new AccountReportRequest(7L, List.of("ig_reach"), null, null),
                new AccountReportRequest(12L, List.of("ig_reach"), null, null))));

        assertThat(res.reports()).extracting(AccountReportResponse::accountId).containsExactly(7L, 12L);
        // cada cuenta consultó con SU client_id (aislamiento multi-tenant)
        verify(queryRepository).accountMetricSeries(eq(1L), eq(7L), any(), any(), any());
        verify(queryRepository).accountMetricSeries(eq(2L), eq(12L), any(), any(), any());
    }

    @Test
    void batchOverLimitIsBadRequestAndProcessesNothing() {
        List<AccountReportRequest> tooMany = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> new AccountReportRequest((long) i, List.of("ig_reach"), null, null))
                .toList();

        assertThatThrownBy(() -> service.batchReport(new BatchReportRequest(tooMany)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
        verify(accountRepository, never()).findById(anyLong());
    }

    @Test
    void batchEmptyIsBadRequest() {
        assertThatThrownBy(() -> service.batchReport(new BatchReportRequest(List.of())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void batchFailsWholeWhenAnyAccountMissing() {
        stubMetrics("ig_reach");
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(accountRepository.findById(404L)).thenReturn(Optional.empty()); // cuenta inexistente
        when(queryRepository.accountMetricSeries(anyLong(), anyLong(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.batchReport(new BatchReportRequest(List.of(
                new AccountReportRequest(7L, List.of("ig_reach"), null, null),
                new AccountReportRequest(404L, List.of("ig_reach"), null, null)))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(404));
    }

    // ---- helpers ----

    private void stubMetrics(String... keys) {
        LinkedHashMap<String, Metric> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, metric(key));
        }
        when(catalog.requireReportMetrics(any())).thenReturn(map);
    }

    private static Metric metric(String key) {
        Metric m = new Metric();
        m.setKey(key);
        m.setLevel(MetricLevel.ACCOUNT);
        m.setUnit("count");
        return m;
    }

    private static SocialAccount account(Long id, Long clientId) {
        SocialAccount a = new SocialAccount();
        a.setId(id);
        a.setClientId(clientId);
        a.setPlatform(Platform.INSTAGRAM);
        a.setStatus(AccountStatus.CONNECTED);
        return a;
    }

    private static Post post(Long id, Long clientId) {
        Post p = new Post();
        p.setId(id);
        p.setClientId(clientId);
        p.setAccountId(7L);
        p.setPlatform(Platform.INSTAGRAM);
        return p;
    }
}
