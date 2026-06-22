package com.filgrama.metrics.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Metric;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.MetricFormat;
import com.filgrama.metrics.dto.PageResponse;
import com.filgrama.metrics.dto.PostListItem;
import com.filgrama.metrics.repository.MetricsQueryRepository;
import com.filgrama.metrics.repository.MetricsQueryRepository.PostRow;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Página de posts de una cuenta en un rango, ordenable por columna del post o por una métrica
 * de post (top posts, ej. {@code sort=ig_post_total_interactions,desc} o el alias {@code engagement}).
 * Multi-tenant: resuelve el {@code client_id} de la cuenta y filtra por él.
 */
@Service
public class AccountPostsService {

    private static final int MAX_SIZE = 100;

    /** Alias 'engagement' → métrica de interacciones de post según la red de la cuenta. */
    private static final Map<String, String> ENGAGEMENT_ALIAS = Map.of(
            "INSTAGRAM", "ig_post_total_interactions",
            "FACEBOOK", "fb_post_engaged_users",
            "TIKTOK", "tt_like_count");

    private enum SortKind { COLUMN, METRIC }

    private record SortSpec(SortKind kind, String field, boolean asc) {
    }

    private final SocialAccountRepository accountRepository;
    private final MetricCatalogService catalog;
    private final MetricsQueryRepository queryRepository;

    public AccountPostsService(SocialAccountRepository accountRepository,
                               MetricCatalogService catalog,
                               MetricsQueryRepository queryRepository) {
        this.accountRepository = accountRepository;
        this.catalog = catalog;
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PostListItem> list(Long accountId, LocalDate from, LocalDate to,
                                           int page, int size, String sort) {
        if (page < 0) {
            throw ApiException.badRequest("'page' no puede ser negativo");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("'size' debe estar entre 1 y %d".formatted(MAX_SIZE));
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("rango inválido: 'from' (%s) > 'to' (%s)".formatted(from, to));
        }

        SocialAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account %d not found".formatted(accountId)));
        Long clientId = account.getClientId();
        String platform = account.getPlatform().name();

        SortSpec sortSpec = parseSort(sort, platform);
        long total = queryRepository.countAccountPosts(clientId, accountId, from, to);
        int offset = page * size;

        List<PostRow> rows = switch (sortSpec.kind()) {
            case COLUMN -> queryRepository.findAccountPostsByColumn(
                    clientId, accountId, from, to, sortSpec.field(), sortSpec.asc(), size, offset);
            case METRIC -> queryRepository.findAccountPostsByMetric(
                    clientId, accountId, from, to, sortSpec.field(), sortSpec.asc(), size, offset);
        };

        boolean byMetric = sortSpec.kind() == SortKind.METRIC;
        List<PostListItem> content = rows.stream().map(r -> toItem(r, byMetric)).toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    /**
     * Parsea {@code sort=field,dir}. {@code field} ∈ {published_at, first_seen_at} ordena por columna;
     * el alias {@code engagement} o cualquier {@code metric_key} de nivel POST ordena por métrica.
     * Default: {@code published_at,desc}.
     */
    private SortSpec parseSort(String sort, String platform) {
        String field = "published_at";
        boolean asc = false;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            field = parts[0].trim();
            if (parts.length > 1) {
                asc = "asc".equalsIgnoreCase(parts[1].trim());
            }
        }
        String lower = field.toLowerCase(Locale.ROOT);
        if (lower.equals("published_at") || lower.equals("first_seen_at")) {
            return new SortSpec(SortKind.COLUMN, lower, asc);
        }
        String requested = field;
        String metricKey = "engagement".equals(lower) ? ENGAGEMENT_ALIAS.get(platform) : field;
        Metric metric = catalog.find(metricKey)
                .orElseThrow(() -> ApiException.badRequest(
                        "sort inválido: '%s' (use published_at, first_seen_at o un metric_key de post)"
                                .formatted(requested)));
        if (metric.getLevel() != MetricLevel.POST) {
            throw ApiException.badRequest("la métrica '%s' no es de nivel POST".formatted(metric.getKey()));
        }
        return new SortSpec(SortKind.METRIC, metric.getKey(), asc);
    }

    private static PostListItem toItem(PostRow r, boolean includeSortValue) {
        return new PostListItem(
                r.id(), r.accountId(), r.platform(), r.externalPostId(), r.postType(),
                r.permalink(), r.caption(), r.remoteThumbnailUrl(), r.publishedAt(),
                includeSortValue ? MetricFormat.clean(r.sortValue()) : null);
    }
}
