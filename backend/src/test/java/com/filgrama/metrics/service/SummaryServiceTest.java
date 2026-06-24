package com.filgrama.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.metrics.dto.PlatformSummary;
import com.filgrama.metrics.dto.SummaryMetric;
import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.repository.MetricsQueryRepository;
import com.filgrama.metrics.repository.MetricsQueryRepository.MetricAgg;
import com.filgrama.metrics.repository.MetricsQueryRepository.SeriesRow;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

class SummaryServiceTest {

    private static final LocalDate FROM = LocalDate.parse("2026-06-01");
    private static final LocalDate TO = LocalDate.parse("2026-06-30");

    private ClientRepository clientRepository;
    private SocialAccountRepository accountRepository;
    private MetricCatalogService catalog;
    private MetricsQueryRepository queryRepository;
    private SummaryService service;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        accountRepository = mock(SocialAccountRepository.class);
        catalog = mock(MetricCatalogService.class);
        queryRepository = mock(MetricsQueryRepository.class);
        service = new SummaryService(clientRepository, accountRepository, catalog, queryRepository);
        when(clientRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void aggregatesPerPlatformWithDerivedEngagementAndGrowth() {
        when(accountRepository.findByClientId(1L)).thenReturn(List.of(
                account(Platform.INSTAGRAM), account(Platform.FACEBOOK)));
        when(catalog.list("INSTAGRAM", "ACCOUNT")).thenReturn(List.of(
                item("ig_followers_count"), item("ig_reach"), item("ig_total_interactions")));
        when(catalog.list("FACEBOOK", "ACCOUNT")).thenReturn(List.of(item("fb_page_views")));

        when(queryRepository.accountMetricAgg(eq(1L), eq("INSTAGRAM"), eq("ig_followers_count"), any(), any()))
                .thenReturn(new MetricAgg(bd("100000"), bd("12500"), bd("12000")));
        when(queryRepository.accountMetricAgg(eq(1L), eq("INSTAGRAM"), eq("ig_reach"), any(), any()))
                .thenReturn(new MetricAgg(bd("100000"), bd("13010"), bd("12450")));
        when(queryRepository.accountMetricAgg(eq(1L), eq("INSTAGRAM"), eq("ig_total_interactions"), any(), any()))
                .thenReturn(new MetricAgg(bd("3400"), bd("120"), bd("90")));
        when(queryRepository.accountMetricAgg(eq(1L), eq("FACEBOOK"), eq("fb_page_views"), any(), any()))
                .thenReturn(new MetricAgg(bd("5000"), bd("200"), bd("150")));

        SummaryResponse res = service.summary(1L, FROM, TO, null);

        PlatformSummary ig = res.platforms().stream()
                .filter(p -> p.platform().equals("INSTAGRAM")).findFirst().orElseThrow();
        assertThat(ig.metrics()).extracting(m -> m.metric())
                .containsExactly("ig_followers_count", "ig_reach", "ig_total_interactions");
        // engagement_rate = interactions.total(3400) / reach.total(100000) = 0.034
        assertThat(ig.engagementRate()).isEqualByComparingTo("0.034");
        // follower_growth = followers.latest(12500) - earliest(12000) = 500
        assertThat(ig.followerGrowth()).isEqualByComparingTo("500");
    }

    @Test
    void platformFilterRestrictsResult() {
        when(accountRepository.findByClientId(1L)).thenReturn(List.of(
                account(Platform.INSTAGRAM), account(Platform.FACEBOOK)));
        when(catalog.list("FACEBOOK", "ACCOUNT")).thenReturn(List.of(item("fb_page_views")));
        when(queryRepository.accountMetricAgg(eq(1L), eq("FACEBOOK"), eq("fb_page_views"), any(), any()))
                .thenReturn(new MetricAgg(bd("5000"), bd("200"), bd("150")));

        SummaryResponse res = service.summary(1L, FROM, TO, "facebook");

        assertThat(res.platforms()).extracting(PlatformSummary::platform).containsExactly("FACEBOOK");
        // Multi-tenant: la consulta de agregado se hizo con client_id = 1.
        verify(queryRepository).accountMetricAgg(eq(1L), eq("FACEBOOK"), eq("fb_page_views"), any(), any());
    }

    @Test
    void unknownClientIsNotFound() {
        when(clientRepository.existsById(42L)).thenReturn(false);
        assertThatThrownBy(() -> service.summary(42L, FROM, TO, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(404));
    }

    @Test
    void clientWithoutAccountsReturnsEmptyPlatforms() {
        when(accountRepository.findByClientId(1L)).thenReturn(List.of());
        SummaryResponse res = service.summary(1L, FROM, TO, null);
        assertThat(res.platforms()).isEmpty();
    }

    // ---- TAREA C: summary account-scoped (reporte por cuenta) ----

    @Test
    void summaryForAccountsAggregatesOnlyGivenAccountsAndCombinesPerPlatform() {
        // Cliente con 2 cuentas IG (11, 12) + 1 FACEBOOK (13); pedimos SÓLO las dos IG.
        when(accountRepository.findByClientId(1L)).thenReturn(List.of(
                account(11L, Platform.INSTAGRAM),
                account(12L, Platform.INSTAGRAM),
                account(13L, Platform.FACEBOOK)));
        when(catalog.list("INSTAGRAM", "ACCOUNT")).thenReturn(List.of(
                item("ig_followers_count"), item("ig_reach"), item("ig_total_interactions")));

        // Serie por cuenta (ya ordenada por captured_at asc): primero=earliest, último=latest.
        when(queryRepository.accountMetricSeries(eq(1L), eq(11L), any(), eq(FROM), eq(TO))).thenReturn(List.of(
                row("ig_followers_count", 100), row("ig_followers_count", 120),
                row("ig_reach", 1000),
                row("ig_total_interactions", 34)));
        when(queryRepository.accountMetricSeries(eq(1L), eq(12L), any(), eq(FROM), eq(TO))).thenReturn(List.of(
                row("ig_followers_count", 200), row("ig_followers_count", 230),
                row("ig_reach", 3000),
                row("ig_total_interactions", 66)));

        SummaryResponse res = service.summaryForAccounts(1L, FROM, TO, List.of(11L, 12L));

        assertThat(res.platforms()).extracting(PlatformSummary::platform).containsExactly("INSTAGRAM");
        PlatformSummary ig = res.platforms().get(0);
        // followers: latest = 120 + 230 = 350; total = 220 + 430 = 650 (suma por cuenta del total/último).
        SummaryMetric followers = metric(ig, "ig_followers_count");
        assertThat(followers.latest()).isEqualByComparingTo("350");
        assertThat(followers.total()).isEqualByComparingTo("650");
        // reach total = 1000 + 3000 = 4000; interactions total = 34 + 66 = 100.
        assertThat(metric(ig, "ig_reach").total()).isEqualByComparingTo("4000");
        assertThat(metric(ig, "ig_total_interactions").total()).isEqualByComparingTo("100");
        // engagement = interactions.total(100) / reach.total(4000) = 0.025.
        assertThat(ig.engagementRate()).isEqualByComparingTo("0.025");
        // follower_growth = followers.latest(350) - earliest(100 + 200 = 300) = 50.
        assertThat(ig.followerGrowth()).isEqualByComparingTo("50");
        // La cuenta FACEBOOK (no pedida) no se consulta.
        verify(queryRepository, never()).accountMetricSeries(eq(1L), eq(13L), any(), any(), any());
    }

    @Test
    void summaryForAccountsWithNoMatchingAccountsReturnsEmptyPlatforms() {
        // accountIds que no son del cliente: ninguna red (la validación dura de pertenencia es del reporte).
        when(accountRepository.findByClientId(1L)).thenReturn(List.of(account(11L, Platform.INSTAGRAM)));

        SummaryResponse res = service.summaryForAccounts(1L, FROM, TO, List.of(999L));

        assertThat(res.platforms()).isEmpty();
    }

    @Test
    void summaryForAccountsUnknownClientIsNotFound() {
        when(clientRepository.existsById(42L)).thenReturn(false);
        assertThatThrownBy(() -> service.summaryForAccounts(42L, FROM, TO, List.of(1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(404));
    }

    private static SummaryMetric metric(PlatformSummary platform, String key) {
        return platform.metrics().stream().filter(m -> m.metric().equals(key)).findFirst().orElseThrow();
    }

    private static SeriesRow row(String metricKey, long value) {
        return new SeriesRow(metricKey, FROM, BigDecimal.valueOf(value));
    }

    private static SocialAccount account(Platform platform) {
        SocialAccount a = new SocialAccount();
        a.setClientId(1L);
        a.setPlatform(platform);
        return a;
    }

    private static SocialAccount account(Long id, Platform platform) {
        SocialAccount a = account(platform);
        a.setId(id);
        return a;
    }

    private static MetricCatalogItem item(String key) {
        return new MetricCatalogItem(key, key, null, "ACCOUNT", "count", "CORE", "ACTIVE");
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
