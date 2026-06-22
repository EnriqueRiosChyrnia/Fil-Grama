package com.filgrama.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.Metric;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.PageResponse;
import com.filgrama.metrics.dto.PostListItem;
import com.filgrama.metrics.repository.MetricsQueryRepository;
import com.filgrama.metrics.repository.MetricsQueryRepository.PostRow;
import com.filgrama.repository.SocialAccountRepository;

class AccountPostsServiceTest {

    private SocialAccountRepository accountRepository;
    private MetricCatalogService catalog;
    private MetricsQueryRepository queryRepository;
    private AccountPostsService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(SocialAccountRepository.class);
        catalog = mock(MetricCatalogService.class);
        queryRepository = mock(MetricsQueryRepository.class);
        service = new AccountPostsService(accountRepository, catalog, queryRepository);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L, Platform.INSTAGRAM)));
    }

    @Test
    void defaultColumnSortPaginates() {
        when(queryRepository.countAccountPosts(eq(1L), eq(7L), any(), any())).thenReturn(2L);
        when(queryRepository.findAccountPostsByColumn(eq(1L), eq(7L), any(), any(),
                eq("published_at"), eq(false), eq(20), eq(0)))
                .thenReturn(List.of(row(1L, null), row(2L, null)));

        PageResponse<PostListItem> page = service.list(7L, null, null, 0, 20, "published_at,desc");

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.content().get(0).sortValue()).isNull(); // sort por columna → sin sortValue
    }

    @Test
    void metricSortUsesLateralJoinAndExposesSortValue() {
        when(catalog.find("ig_post_likes")).thenReturn(Optional.of(metric("ig_post_likes", MetricLevel.POST)));
        when(queryRepository.countAccountPosts(eq(1L), eq(7L), any(), any())).thenReturn(1L);
        when(queryRepository.findAccountPostsByMetric(eq(1L), eq(7L), any(), any(),
                eq("ig_post_likes"), eq(false), eq(20), eq(0)))
                .thenReturn(List.of(row(3L, new BigDecimal("42"))));

        PageResponse<PostListItem> page = service.list(7L, null, null, 0, 20, "ig_post_likes,desc");

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).sortValue()).isEqualByComparingTo("42");
    }

    @Test
    void engagementAliasResolvesToPlatformMetric() {
        when(catalog.find("ig_post_total_interactions"))
                .thenReturn(Optional.of(metric("ig_post_total_interactions", MetricLevel.POST)));
        when(queryRepository.countAccountPosts(eq(1L), eq(7L), any(), any())).thenReturn(0L);
        when(queryRepository.findAccountPostsByMetric(eq(1L), eq(7L), any(), any(),
                eq("ig_post_total_interactions"), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.list(7L, null, null, 0, 20, "engagement,desc");

        verify(queryRepository).findAccountPostsByMetric(eq(1L), eq(7L), any(), any(),
                eq("ig_post_total_interactions"), eq(false), eq(20), eq(0));
    }

    @Test
    void unknownSortFieldIsBadRequest() {
        when(catalog.find("bogus")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(7L, null, null, 0, 20, "bogus,desc"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void sortingByAccountLevelMetricIsBadRequest() {
        when(catalog.find("ig_reach")).thenReturn(Optional.of(metric("ig_reach", MetricLevel.ACCOUNT)));
        assertThatThrownBy(() -> service.list(7L, null, null, 0, 20, "ig_reach,desc"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void invalidSizeIsBadRequest() {
        assertThatThrownBy(() -> service.list(7L, null, null, 0, 0, "published_at,desc"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void missingAccountIsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(99L, null, null, 0, 20, "published_at,desc"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(404));
    }

    private static SocialAccount account(Long id, Long clientId, Platform platform) {
        SocialAccount a = new SocialAccount();
        a.setId(id);
        a.setClientId(clientId);
        a.setPlatform(platform);
        return a;
    }

    private static Metric metric(String key, MetricLevel level) {
        Metric m = new Metric();
        m.setKey(key);
        m.setLevel(level);
        return m;
    }

    private static PostRow row(Long id, BigDecimal sortValue) {
        return new PostRow(id, 7L, "INSTAGRAM", "ext-" + id, "IMAGE",
                "https://example/" + id, "caption", "https://thumb/" + id, Instant.parse("2026-06-01T03:00:00Z"),
                sortValue);
    }
}
