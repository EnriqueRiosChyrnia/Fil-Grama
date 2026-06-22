package com.filgrama.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.AccountMetricSnapshot;
import com.filgrama.domain.Metric;
import com.filgrama.domain.Post;
import com.filgrama.domain.PostMetricSnapshot;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.AccountSeriesResponse;
import com.filgrama.metrics.dto.PostSeriesResponse;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;

class MetricSeriesServiceTest {

    private MetricCatalogService catalog;
    private SocialAccountRepository accountRepository;
    private PostRepository postRepository;
    private AccountMetricSnapshotRepository accountSnapshots;
    private PostMetricSnapshotRepository postSnapshots;
    private MetricSeriesService service;

    @BeforeEach
    void setUp() {
        catalog = mock(MetricCatalogService.class);
        accountRepository = mock(SocialAccountRepository.class);
        postRepository = mock(PostRepository.class);
        accountSnapshots = mock(AccountMetricSnapshotRepository.class);
        postSnapshots = mock(PostMetricSnapshotRepository.class);
        service = new MetricSeriesService(catalog, accountRepository, postRepository,
                accountSnapshots, postSnapshots);
        when(catalog.requireMetric(anyString())).thenReturn(metric("ig_reach", MetricLevel.ACCOUNT));
    }

    @Test
    void accountSeriesFiltersByRangeAndKeepsOrder() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(accountSnapshots.findByAccountIdAndMetricKeyOrderByCapturedAtAsc(7L, "ig_reach"))
                .thenReturn(List.of(
                        acctSnap(1L, LocalDate.parse("2026-05-31"), "12000"),
                        acctSnap(1L, LocalDate.parse("2026-06-01"), "12450"),
                        acctSnap(1L, LocalDate.parse("2026-06-02"), "13010"),
                        acctSnap(1L, LocalDate.parse("2026-06-30"), "20000")));

        AccountSeriesResponse res = service.accountSeries(7L, "ig_reach",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-02"), "day");

        assertThat(res.accountId()).isEqualTo(7L);
        assertThat(res.metric()).isEqualTo("ig_reach");
        assertThat(res.granularity()).isEqualTo("day");
        assertThat(res.points()).extracting(p -> p.value().toPlainString())
                .containsExactly("12450", "13010");
    }

    @Test
    void emptyRangeReturnsEmptySeriesNotError() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(accountSnapshots.findByAccountIdAndMetricKeyOrderByCapturedAtAsc(7L, "ig_reach"))
                .thenReturn(List.of(acctSnap(1L, LocalDate.parse("2026-01-01"), "100")));

        AccountSeriesResponse res = service.accountSeries(7L, "ig_reach",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), "day");

        assertThat(res.points()).isEmpty();
    }

    @Test
    void multiTenantExcludesOtherClientSnapshots() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L)));
        when(accountSnapshots.findByAccountIdAndMetricKeyOrderByCapturedAtAsc(7L, "ig_reach"))
                .thenReturn(List.of(
                        acctSnap(1L, LocalDate.parse("2026-06-01"), "12450"),
                        acctSnap(2L, LocalDate.parse("2026-06-02"), "999999"))); // otro cliente

        AccountSeriesResponse res = service.accountSeries(7L, "ig_reach", null, null, "day");

        assertThat(res.points()).hasSize(1);
        assertThat(res.points().get(0).value().toPlainString()).isEqualTo("12450");
    }

    @Test
    void unknownMetricPropagatesUnprocessable() {
        when(catalog.requireMetric("bogus")).thenThrow(ApiException.unprocessable("metric_key 'bogus'"));
        assertThatThrownBy(() -> service.accountSeries(7L, "bogus", null, null, "day"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(422));
    }

    @Test
    void invalidRangeIsBadRequest() {
        assertThatThrownBy(() -> service.accountSeries(7L, "ig_reach",
                LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-01"), "day"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void missingAccountIsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accountSeries(99L, "ig_reach", null, null, "day"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(404));
    }

    @Test
    void postSeriesFiltersByClientAndRange() {
        when(postRepository.findById(5L)).thenReturn(Optional.of(post(5L, 1L)));
        when(postSnapshots.findByPostIdAndMetricKeyOrderByCapturedAtAsc(5L, "ig_reach"))
                .thenReturn(List.of(
                        postSnap(1L, LocalDate.parse("2026-06-01"), "10"),
                        postSnap(2L, LocalDate.parse("2026-06-02"), "777"))); // otro cliente

        PostSeriesResponse res = service.postSeries(5L, "ig_reach", null, null);

        assertThat(res.postId()).isEqualTo(5L);
        assertThat(res.points()).hasSize(1);
        assertThat(res.points().get(0).value().toPlainString()).isEqualTo("10");
    }

    private static Metric metric(String key, MetricLevel level) {
        Metric m = new Metric();
        m.setKey(key);
        m.setLevel(level);
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

    private static AccountMetricSnapshot acctSnap(Long clientId, LocalDate date, String value) {
        AccountMetricSnapshot s = new AccountMetricSnapshot();
        s.setClientId(clientId);
        s.setAccountId(7L);
        s.setMetricKey("ig_reach");
        s.setValue(new BigDecimal(value));
        s.setCaptureDate(date);
        s.setCapturedAt(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        return s;
    }

    private static PostMetricSnapshot postSnap(Long clientId, LocalDate date, String value) {
        PostMetricSnapshot s = new PostMetricSnapshot();
        s.setClientId(clientId);
        s.setAccountId(7L);
        s.setPostId(5L);
        s.setMetricKey("ig_reach");
        s.setValue(new BigDecimal(value));
        s.setCaptureDate(date);
        s.setCapturedAt(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        return s;
    }
}
