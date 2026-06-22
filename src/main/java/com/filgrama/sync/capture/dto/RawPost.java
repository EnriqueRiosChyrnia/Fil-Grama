package com.filgrama.sync.capture.dto;

import java.time.Instant;

import com.filgrama.domain.enums.PostType;

/**
 * Metadatos de una publicación tal como los entrega el provider (feed/reel/video/story).
 * El orquestador hace upsert de {@code posts} por {@code (account_id, external_post_id)}.
 *
 * @param ephemeral  {@code true} para stories (viven 24 h); fija {@code expiresAt}.
 */
public record RawPost(
        String externalPostId,
        PostType postType,
        String permalink,
        String caption,
        String remoteMediaUrl,
        String remoteThumbnailUrl,
        Instant publishedAt,
        boolean ephemeral,
        Instant expiresAt) {
}
