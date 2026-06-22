package com.filgrama.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.Metric;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.MetricState;
import com.filgrama.domain.enums.MetricTier;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.repository.MetricRepository;

class MetricCatalogServiceTest {

    private MetricRepository metricRepository;
    private MetricCatalogService service;

    @BeforeEach
    void setUp() {
        metricRepository = mock(MetricRepository.class);
        service = new MetricCatalogService(metricRepository);
        when(metricRepository.findByTier(MetricTier.CORE)).thenReturn(List.of(
                metric("ig_reach", "INSTAGRAM", MetricLevel.ACCOUNT, MetricState.ACTIVE),
                metric("ig_post_likes", "INSTAGRAM", MetricLevel.POST, MetricState.ACTIVE),
                metric("fb_page_views", "FACEBOOK", MetricLevel.ACCOUNT, MetricState.ACTIVE),
                metric("all_health", null, MetricLevel.ACCOUNT, MetricState.ACTIVE),
                metric("ig_impressions_old", "INSTAGRAM", MetricLevel.ACCOUNT, MetricState.DEPRECATED)));
    }

    @Test
    void listExcludesNonActiveAndSortsByKey() {
        List<MetricCatalogItem> items = service.list(null, null);
        assertThat(items).extracting(MetricCatalogItem::key)
                .containsExactly("all_health", "fb_page_views", "ig_post_likes", "ig_reach");
    }

    @Test
    void platformFilterIncludesNullPlatformMetrics() {
        List<MetricCatalogItem> items = service.list("instagram", null);
        assertThat(items).extracting(MetricCatalogItem::key)
                .containsExactlyInAnyOrder("ig_reach", "ig_post_likes", "all_health");
        assertThat(items).extracting(MetricCatalogItem::key).doesNotContain("fb_page_views");
    }

    @Test
    void levelFilterApplies() {
        List<MetricCatalogItem> items = service.list("INSTAGRAM", "POST");
        assertThat(items).extracting(MetricCatalogItem::key).containsExactly("ig_post_likes");
    }

    @Test
    void invalidLevelIsBadRequest() {
        assertThatThrownBy(() -> service.list(null, "WEEKLY"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void requireMetricReturnsEntityWhenPresent() {
        when(metricRepository.findById("ig_reach"))
                .thenReturn(Optional.of(metric("ig_reach", "INSTAGRAM", MetricLevel.ACCOUNT, MetricState.ACTIVE)));
        assertThat(service.requireMetric("ig_reach").getKey()).isEqualTo("ig_reach");
    }

    @Test
    void requireMetricUnknownIsUnprocessable() {
        when(metricRepository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireMetric("nope"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(422));
    }

    @Test
    void requireMetricBlankIsBadRequest() {
        assertThatThrownBy(() -> service.requireMetric("  "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    private static Metric metric(String key, String platform, MetricLevel level, MetricState state) {
        Metric m = new Metric();
        m.setKey(key);
        m.setDisplayName(key);
        m.setPlatform(platform);
        m.setLevel(level);
        m.setUnit("count");
        m.setTier(MetricTier.CORE);
        m.setState(state);
        return m;
    }
}
