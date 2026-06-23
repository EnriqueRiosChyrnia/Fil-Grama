package com.filgrama.reports.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.reports.render.ThumbnailLoader.Thumbnail;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.storage.StorageException;
import com.filgrama.storage.StoragePort;

/**
 * Resiliencia del cargador de miniaturas: la miniatura es <b>accesoria</b> (spec/08 "si falta, queda
 * null / cae al remoto"). Un fallo de storage al leer el binario NO debe propagarse ni romper el
 * armado del reporte — exactamente el caso que tiraba 500 (objeto faltante/MinIO caído en el
 * {@code assemble}, antes del try/catch del service).
 */
class ThumbnailLoaderTest {

    private static final Long POST_ID = 7L;
    private static final String REMOTE = "https://cdn.example/thumb.jpg";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private MediaAssetRepository mediaAssets;
    private StoragePort storage;
    private ThumbnailLoader loader;

    @BeforeEach
    void setUp() {
        mediaAssets = mock(MediaAssetRepository.class);
        storage = mock(StoragePort.class);
        loader = new ThumbnailLoader(mediaAssets, storage);
    }

    private MediaAsset thumbnailAsset() {
        MediaAsset a = new MediaAsset();
        a.setPostId(POST_ID);
        a.setKind(MediaKind.THUMBNAIL);
        a.setStoragePath("demo/thumbs/x.jpg");
        a.setContentType("image/jpeg");
        return a;
    }

    @Test
    void cachedThumbnailIsReturnedAsDataUri() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        when(storage.get("demo/thumbs/x.jpg")).thenReturn(PNG);

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void storageRuntimeFailureFallsBackToRemoteWithoutPropagating() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        // MinIO inalcanzable: el adapter podría filtrar una RuntimeException distinta de StorageException.
        when(storage.get("demo/thumbs/x.jpg")).thenThrow(new RuntimeException("connection refused"));

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).isNull();
        assertThat(thumb.remoteUrl()).isEqualTo(REMOTE);
    }

    @Test
    void storageExceptionFallsBackToRemote() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        when(storage.get("demo/thumbs/x.jpg")).thenThrow(new StorageException("no existe el objeto"));

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).isNull();
        assertThat(thumb.remoteUrl()).isEqualTo(REMOTE);
    }
}
