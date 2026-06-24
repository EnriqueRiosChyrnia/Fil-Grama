package com.filgrama.sync.job;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Post;
import com.filgrama.domain.RawApiPayload;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.RawScope;
import com.filgrama.media.MediaService;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.repository.RawApiPayloadRepository;
import com.filgrama.sync.capture.InsightsProvider;
import com.filgrama.sync.capture.InsightsProviderRegistry;
import com.filgrama.sync.capture.ThumbnailFetcher;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;
import com.filgrama.sync.capture.dto.StoryCapture;
import com.filgrama.sync.derive.SnapshotDeriver;

/**
 * Pipeline por cuenta (orden estricto, spec/10): (1) refrescar token → (2) consultar API →
 * (3) guardar crudo → (4) derivar snapshots (upsert idempotente) → (5) upsert posts →
 * (6) [stories IG] cachear miniatura. Devuelve cuántas métricas capturó.
 *
 * <p><b>{@code REQUIRES_NEW}:</b> cada cuenta corre en su propia transacción. Si falla, solo se
 * revierte SU trabajo (atómico por cuenta) y el orquestador sigue con las demás → corrida
 * {@code PARTIAL}. El crudo de las cuentas OK queda persistido (append puro). Logs sin tokens.
 */
@Component
public class AccountSyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(AccountSyncProcessor.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Asuncion");

    private final InsightsProviderRegistry registry;
    private final TokenAccessor tokenAccessor;
    private final Retrier retrier;
    private final SnapshotDeriver deriver;
    private final RawApiPayloadRepository rawRepository;
    private final PostRepository postRepository;
    private final ClientRepository clientRepository;
    private final MediaService mediaService;
    private final ThumbnailFetcher thumbnailFetcher;

    public AccountSyncProcessor(InsightsProviderRegistry registry, TokenAccessor tokenAccessor,
            Retrier retrier, SnapshotDeriver deriver, RawApiPayloadRepository rawRepository,
            PostRepository postRepository, ClientRepository clientRepository, MediaService mediaService,
            ThumbnailFetcher thumbnailFetcher) {
        this.registry = registry;
        this.tokenAccessor = tokenAccessor;
        this.retrier = retrier;
        this.deriver = deriver;
        this.rawRepository = rawRepository;
        this.postRepository = postRepository;
        this.clientRepository = clientRepository;
        this.mediaService = mediaService;
        this.thumbnailFetcher = thumbnailFetcher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int process(SocialAccount account, Long runId) {
        String token = tokenAccessor.resolveAccessToken(account);
        InsightsProvider provider = registry.resolve(account.getPlatform());
        Instant now = Instant.now();
        LocalDate captureDate = now.atZone(clientZone(account.getClientId())).toLocalDate();
        int captured = 0;

        // (2-4) insights de cuenta
        AccountCapture accountCapture = retrier.withRetry("acct:" + account.getId(),
                () -> provider.fetchAccountInsights(account, token));
        saveRaw(runId, account, RawScope.ACCOUNT, null, accountCapture.endpoint(), accountCapture.rawJson(), now);
        captured += deriver.deriveAccount(account, accountCapture, now, captureDate);

        // (2-5) lista de posts + insights por post
        PostsListCapture list = retrier.withRetry("posts:" + account.getId(),
                () -> provider.fetchPosts(account, token));
        saveRaw(runId, account, RawScope.POSTS_LIST, null, list.endpoint(), list.rawJson(), now);
        for (RawPost rawPost : list.posts()) {
            Post post = upsertPost(account, rawPost, now);
            PostInsightsCapture insights = retrier.withRetry("post:" + post.getId(),
                    () -> provider.fetchPostInsights(account, rawPost, token));
            saveRaw(runId, account, RawScope.POST, post.getId(), insights.endpoint(), insights.rawJson(), now);
            captured += deriver.derivePost(account, post, insights.metrics(), now, captureDate);
            // (6) miniatura real para el reporte: baja remote_thumbnail_url → storage (TAREA F).
            cacheThumbnailBestEffort(post);
        }

        // (CU8) stories de Instagram: métricas finales + miniatura cacheada
        for (StoryCapture story : provider.fetchStories(account, token)) {
            Post post = upsertPost(account, story.meta(), now);
            saveRaw(runId, account, RawScope.POST, post.getId(), story.endpoint(), story.rawJson(), now);
            captured += deriver.derivePost(account, post, story.metrics(), now, captureDate);
            if (story.thumbnailBytes() != null && story.thumbnailBytes().length > 0) {
                if (!mediaService.hasThumbnail(post.getId())) { // idempotente entre corridas
                    mediaService.cacheThumbnailQuietly(post, story.thumbnailBytes(), story.thumbnailContentType());
                }
            } else {
                // El provider no trajo bytes (camino real): la bajamos desde la URL como los posts.
                cacheThumbnailBestEffort(post);
            }
        }
        return captured;
    }

    /**
     * Cachea la miniatura del post desde {@code remote_thumbnail_url}, <b>best-effort</b> (TAREA F):
     * salta si no hay URL o si ya está cacheada (idempotente entre corridas), baja los bytes y los
     * sube en una tx aislada. Cualquier fallo (red, storage, etc.) se loguea y se traga: la captura
     * de posts/métricas de la cuenta NO debe romperse por una miniatura.
     */
    private void cacheThumbnailBestEffort(Post post) {
        String url = post.getRemoteThumbnailUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (mediaService.hasThumbnail(post.getId())) {
                return;
            }
            thumbnailFetcher.fetch(url).ifPresent(img ->
                    mediaService.cacheThumbnailQuietly(post, img.bytes(), img.contentType()));
        } catch (RuntimeException e) {
            log.warn("No se pudo cachear la miniatura del post {}: {}", post.getId(), e.getMessage());
        }
    }

    private Post upsertPost(SocialAccount account, RawPost rawPost, Instant now) {
        Post post = postRepository.findByAccountIdAndExternalPostId(account.getId(), rawPost.externalPostId())
                .orElseGet(() -> {
                    Post p = new Post();
                    p.setClientId(account.getClientId());
                    p.setAccountId(account.getId());
                    p.setPlatform(account.getPlatform());
                    p.setExternalPostId(rawPost.externalPostId());
                    p.setFirstSeenAt(now);
                    return p;
                });
        post.setPostType(rawPost.postType());
        post.setPermalink(rawPost.permalink());
        post.setCaption(rawPost.caption());
        post.setRemoteMediaUrl(rawPost.remoteMediaUrl());
        post.setRemoteThumbnailUrl(rawPost.remoteThumbnailUrl());
        post.setEphemeral(rawPost.ephemeral());
        post.setPublishedAt(rawPost.publishedAt());
        post.setExpiresAt(rawPost.expiresAt());
        return postRepository.save(post);
    }

    private void saveRaw(Long runId, SocialAccount account, RawScope scope, Long postId,
            String endpoint, String payloadJson, Instant capturedAt) {
        RawApiPayload raw = new RawApiPayload();
        raw.setRunId(runId);
        raw.setClientId(account.getClientId());
        raw.setAccountId(account.getId());
        raw.setPlatform(account.getPlatform());
        raw.setScope(scope);
        raw.setPostId(postId);
        raw.setEndpoint(endpoint);
        raw.setPayload(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        raw.setCapturedAt(capturedAt);
        rawRepository.save(raw);
    }

    private ZoneId clientZone(Long clientId) {
        return clientRepository.findById(clientId)
                .map(client -> {
                    try {
                        return ZoneId.of(client.getTimezone());
                    } catch (DateTimeException | NullPointerException ex) {
                        log.warn("Timezone inválida '{}' para cliente {}; uso {}",
                                client.getTimezone(), clientId, DEFAULT_ZONE);
                        return DEFAULT_ZONE;
                    }
                })
                .orElse(DEFAULT_ZONE);
    }
}
