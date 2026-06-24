package com.filgrama.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.Post;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.error.ApiException;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.storage.StoragePort;
import com.filgrama.storage.StoredObject;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock StoragePort storage;
    @Mock MediaAssetRepository mediaAssets;
    @Mock MediaAssetQueryRepository mediaAssetQuery;
    @Mock PostRepository posts;

    MediaService service;

    private static final byte[] IMAGE = "fake-thumbnail-bytes".getBytes(StandardCharsets.UTF_8);

    void initService() {
        service = new MediaService(storage, mediaAssets, mediaAssetQuery, posts);
    }

    @Test
    void cacheThumbnail_uploadsBinary_andPersistsOnlyMetadataRow() {
        initService();
        Post post = postMock(42L, 7L, true);
        when(storage.put(any(), eq(IMAGE), eq("image/jpeg")))
                .thenAnswer(inv -> new StoredObject(inv.getArgument(0), IMAGE, "image/jpeg"));
        when(mediaAssets.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAsset saved = service.cacheThumbnail(post, IMAGE, "image/jpeg");

        // 1. el binario se subió al storage con una key bien formada
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq(IMAGE), eq("image/jpeg"));
        assertThat(key.getValue())
                .startsWith("clients/7/posts/42/thumb-")
                .endsWith(".jpg");

        // 2. la fila media_assets guarda solo ruta + metadata (NADA del binario en Postgres)
        ArgumentCaptor<MediaAsset> row = ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssets).save(row.capture());
        MediaAsset asset = row.getValue();
        assertThat(asset.getKind()).isEqualTo(MediaKind.THUMBNAIL);
        assertThat(asset.getStoragePath()).isEqualTo(key.getValue());
        assertThat(asset.getContentType()).isEqualTo("image/jpeg");
        assertThat(asset.getBytes()).isEqualTo(IMAGE.length);
        assertThat(asset.getPostId()).isEqualTo(42L);
        assertThat(asset.getClientId()).isEqualTo(7L);
        assertThat(asset.getCapturedAt()).isNotNull();
        assertThat(asset.getPurgeAfter()).isNotNull(); // story → retención
        assertThat(saved).isSameAs(asset);
    }

    @Test
    void cacheThumbnail_nonEphemeralPost_hasNoPurgeAfter() {
        initService();
        Post post = postMock(1L, 1L, false);
        when(storage.put(any(), any(), any()))
                .thenAnswer(inv -> new StoredObject(inv.getArgument(0), IMAGE, "image/png"));
        when(mediaAssets.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAsset saved = service.cacheThumbnail(post, IMAGE, "image/png");

        assertThat(saved.getPurgeAfter()).isNull();
    }

    @Test
    void cacheThumbnail_rejectsEmptyBytes() {
        initService();
        Post post = postMock(1L, 1L, false);

        assertThatThrownBy(() -> service.cacheThumbnail(post, new byte[0], "image/jpeg"))
                .isInstanceOf(ApiException.class);
        verify(storage, never()).put(any(), any(), any());
        verify(mediaAssets, never()).save(any());
    }

    @Test
    void hasThumbnail_trueWhenAThumbnailAssetExists() {
        initService();
        MediaAsset thumb = new MediaAsset();
        thumb.setKind(MediaKind.THUMBNAIL);
        when(mediaAssets.findByPostId(42L)).thenReturn(List.of(thumb));

        assertThat(service.hasThumbnail(42L)).isTrue();
    }

    @Test
    void hasThumbnail_falseWhenNoAssets() {
        initService();
        when(mediaAssets.findByPostId(42L)).thenReturn(List.of());

        assertThat(service.hasThumbnail(42L)).isFalse();
    }

    @Test
    void cacheThumbnailQuietly_uploadsAndPersists() {
        initService();
        Post post = postMock(42L, 7L, false);
        when(storage.put(any(), eq(IMAGE), eq("image/jpeg")))
                .thenAnswer(inv -> new StoredObject(inv.getArgument(0), IMAGE, "image/jpeg"));
        when(mediaAssets.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<MediaAsset> saved = service.cacheThumbnailQuietly(post, IMAGE, "image/jpeg");

        verify(storage).put(any(), eq(IMAGE), eq("image/jpeg"));
        verify(mediaAssets).save(any(MediaAsset.class));
        assertThat(saved).isPresent();
        assertThat(saved.get().getKind()).isEqualTo(MediaKind.THUMBNAIL);
        assertThat(saved.get().getPostId()).isEqualTo(42L);
    }

    @Test
    void cacheThumbnailQuietly_swallowsStorageFailure_returnsEmpty() {
        initService();
        Post post = postMock(42L, 7L, false);
        when(storage.put(any(), any(), any()))
                .thenThrow(new com.filgrama.storage.StorageException("S3 caído", null));

        Optional<MediaAsset> result = service.cacheThumbnailQuietly(post, IMAGE, "image/jpeg");

        assertThat(result).isEmpty();              // best-effort: no propaga
        verify(mediaAssets, never()).save(any());  // no fila si no hubo binario
    }

    @Test
    void getThumbnailUrl_prefersPresignedUrl() {
        initService();
        MediaAsset asset = new MediaAsset();
        asset.setStoragePath("clients/1/posts/1/thumb.jpg");
        when(storage.presignedUrl(eq("clients/1/posts/1/thumb.jpg"), any(Duration.class)))
                .thenReturn(Optional.of("http://minio/presigned"));

        assertThat(service.getThumbnailUrl(asset)).contains("http://minio/presigned");
    }

    @Test
    void getThumbnailUrl_fallsBackToRemoteThumbnailUrl_whenNoPresign() {
        initService();
        MediaAsset asset = new MediaAsset();
        asset.setStoragePath("p");
        asset.setPostId(99L);
        when(storage.presignedUrl(any(), any())).thenReturn(Optional.empty());
        Post post = new Post();
        post.setRemoteThumbnailUrl("https://cdn.red/thumb.jpg");
        when(posts.findById(99L)).thenReturn(Optional.of(post));

        assertThat(service.getThumbnailUrl(asset)).contains("https://cdn.red/thumb.jpg");
    }

    @Test
    void purgeExpired_deletesBinaryAndRow_perExpiredAsset() {
        initService();
        MediaAsset a = new MediaAsset();
        a.setStoragePath("k1");
        MediaAsset b = new MediaAsset();
        b.setStoragePath("k2");
        when(mediaAssetQuery.findByPurgeAfterBefore(any())).thenReturn(List.of(a, b));

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(2);
        verify(storage).delete("k1");
        verify(storage).delete("k2");
        verify(mediaAssets).delete(a);
        verify(mediaAssets).delete(b);
    }

    private static Post postMock(long id, long clientId, boolean ephemeral) {
        Post post = org.mockito.Mockito.mock(Post.class);
        // lenient: en el test de bytes vacíos cacheThumbnail corta antes de leer client/ephemeral
        org.mockito.Mockito.lenient().when(post.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(post.getClientId()).thenReturn(clientId);
        org.mockito.Mockito.lenient().when(post.isEphemeral()).thenReturn(ephemeral);
        return post;
    }
}
