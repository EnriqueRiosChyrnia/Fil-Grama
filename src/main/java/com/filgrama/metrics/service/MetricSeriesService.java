package com.filgrama.metrics.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Post;
import com.filgrama.domain.SocialAccount;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.MetricFormat;
import com.filgrama.metrics.dto.AccountSeriesResponse;
import com.filgrama.metrics.dto.PostSeriesResponse;
import com.filgrama.metrics.dto.SeriesPoint;
import com.filgrama.repository.AccountMetricSnapshotRepository;
import com.filgrama.repository.PostMetricSnapshotRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Series temporales de cuenta y de post leídas de las tablas de snapshots (append-only).
 * Multi-tenant: resuelve el {@code client_id} del recurso y sólo devuelve datos de ese cliente.
 * Rango sin datos → serie vacía (NO error).
 */
@Service
public class MetricSeriesService {

    private static final String DEFAULT_GRANULARITY = "day";

    private final MetricCatalogService catalog;
    private final SocialAccountRepository accountRepository;
    private final PostRepository postRepository;
    private final AccountMetricSnapshotRepository accountSnapshots;
    private final PostMetricSnapshotRepository postSnapshots;

    public MetricSeriesService(MetricCatalogService catalog,
                               SocialAccountRepository accountRepository,
                               PostRepository postRepository,
                               AccountMetricSnapshotRepository accountSnapshots,
                               PostMetricSnapshotRepository postSnapshots) {
        this.catalog = catalog;
        this.accountRepository = accountRepository;
        this.postRepository = postRepository;
        this.accountSnapshots = accountSnapshots;
        this.postSnapshots = postSnapshots;
    }

    /** Serie temporal de una métrica de cuenta, ordenada por {@code captured_at}. */
    @Transactional(readOnly = true)
    public AccountSeriesResponse accountSeries(Long accountId, String metric, LocalDate from,
                                               LocalDate to, String granularity) {
        catalog.requireMetric(metric);
        validateRange(from, to);
        SocialAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account %d not found".formatted(accountId)));
        Long clientId = account.getClientId();

        List<SeriesPoint> points = accountSnapshots
                .findByAccountIdAndMetricKeyOrderByCapturedAtAsc(accountId, metric).stream()
                .filter(s -> clientId.equals(s.getClientId()))               // multi-tenant defensivo
                .filter(s -> MetricFormat.inRange(s.getCaptureDate(), from, to))
                .map(s -> new SeriesPoint(s.getCapturedAt(), MetricFormat.clean(s.getValue())))
                .toList();

        return new AccountSeriesResponse(accountId, metric, resolveGranularity(granularity), points);
    }

    /** Serie temporal de una métrica de post, ordenada por {@code captured_at}. */
    @Transactional(readOnly = true)
    public PostSeriesResponse postSeries(Long postId, String metric, LocalDate from, LocalDate to) {
        catalog.requireMetric(metric);
        validateRange(from, to);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post %d not found".formatted(postId)));
        Long clientId = post.getClientId();

        List<SeriesPoint> points = postSnapshots
                .findByPostIdAndMetricKeyOrderByCapturedAtAsc(postId, metric).stream()
                .filter(s -> clientId.equals(s.getClientId()))               // multi-tenant defensivo
                .filter(s -> MetricFormat.inRange(s.getCaptureDate(), from, to))
                .map(s -> new SeriesPoint(s.getCapturedAt(), MetricFormat.clean(s.getValue())))
                .toList();

        return new PostSeriesResponse(postId, metric, points);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
        }
    }

    private static String resolveGranularity(String granularity) {
        return granularity == null || granularity.isBlank() ? DEFAULT_GRANULARITY : granularity.trim();
    }
}
