package com.filgrama.reports.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.media.MediaService;
import com.filgrama.reports.render.ThumbnailLoader.Thumbnail;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.storage.StorageException;
import com.filgrama.storage.StoragePort;
import com.filgrama.sync.capture.ThumbnailFetcher;
import com.filgrama.sync.capture.ThumbnailFetcher.Fetched;

/**
 * Resiliencia y fallback del cargador de miniaturas. La miniatura es <b>accesoria</b> (spec/08 "si
 * falta, queda null / cae al remoto"): un fallo de storage NO debe propagarse ni romper el armado del
 * reporte. Además, cuando no hay binario cacheado, el cargador <b>baja el remoto al vuelo</b> (lo que
 * la web ya hace en el cliente) para que la miniatura aparezca también en el PDF, y lo cachea
 * (auto-heal) para no rebajarlo la próxima.
 */
class ThumbnailLoaderTest {

    private static final Long POST_ID = 7L;
    private static final String REMOTE = "https://cdn.example/thumb.jpg";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private MediaAssetRepository mediaAssets;
    private StoragePort storage;
    private ThumbnailFetcher fetcher;
    private MediaService mediaService;
    private ThumbnailLoader loader;

    @BeforeEach
    void setUp() {
        mediaAssets = mock(MediaAssetRepository.class);
        storage = mock(StoragePort.class);
        fetcher = mock(ThumbnailFetcher.class);
        mediaService = mock(MediaService.class);
        loader = new ThumbnailLoader(mediaAssets, storage, fetcher, mediaService, 4L);
    }

    private MediaAsset thumbnailAsset() {
        MediaAsset a = new MediaAsset();
        a.setPostId(POST_ID);
        a.setKind(MediaKind.THUMBNAIL);
        a.setStoragePath("demo/thumbs/x.jpg");
        a.setContentType("image/jpeg");
        return a;
    }

    // ============================ Cache hit ============================

    @Test
    void cachedThumbnailIsReturnedAsDataUri() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        when(storage.get("demo/thumbs/x.jpg")).thenReturn(PNG);

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void cacheHitDoesNotDownloadRemote() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        when(storage.get("demo/thumbs/x.jpg")).thenReturn(PNG);

        loader.load(POST_ID, REMOTE);

        // Con cache utilizable no se gasta una descarga de red.
        verify(fetcher, never()).fetch(any(), any());
        verify(mediaService, never()).cacheRenderedThumbnail(any(), any(), any());
    }

    @Test
    void webpThumbnailIsTranscodedToPngSoThePdfCanRenderIt() throws Exception {
        // TikTok cachea las miniaturas en WebP, pero openhtmltopdf/PDFBox (vía ImageIO) no lo
        // rasteriza: hay que transcodificar a PNG al armar el data-URI o sale "sin miniatura".
        byte[] webp = Files.readAllBytes(Path.of("src/test/resources/thumbnails/sample.webp"));
        MediaAsset asset = thumbnailAsset();
        asset.setStoragePath("clients/16/posts/376/thumb.webp");
        asset.setContentType("image/webp");
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(asset));
        when(storage.get("clients/16/posts/376/thumb.webp")).thenReturn(webp);

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/png;base64,");
        byte[] embedded = Base64.getDecoder()
                .decode(thumb.dataUri().substring("data:image/png;base64,".length()));
        // Los bytes embebidos son un PNG real y decodificable (lo que el PDF necesita).
        assertThat(ImageIO.read(new ByteArrayInputStream(embedded))).isNotNull();
    }

    // ============================ Storage failure → fallback remoto ============================

    @Test
    void storageRuntimeFailureFallsBackToRemoteWithoutPropagating() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        // MinIO inalcanzable: el adapter podría filtrar una RuntimeException distinta de StorageException.
        when(storage.get("demo/thumbs/x.jpg")).thenThrow(new RuntimeException("connection refused"));
        when(fetcher.fetch(eq(REMOTE), any(Duration.class))).thenReturn(Optional.empty());

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).isNull();
        assertThat(thumb.remoteUrl()).isEqualTo(REMOTE);
    }

    @Test
    void storageExceptionFallsBackToRemote() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        when(storage.get("demo/thumbs/x.jpg")).thenThrow(new StorageException("no existe el objeto"));
        when(fetcher.fetch(eq(REMOTE), any(Duration.class))).thenReturn(Optional.empty());

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).isNull();
        assertThat(thumb.remoteUrl()).isEqualTo(REMOTE);
    }

    @Test
    void cachedBinaryFailureButRemoteAliveStillEmbedsImage() {
        // Cache rota (binario purgado) PERO la URL remota sigue viva: el PDF igual debe mostrar la imagen.
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of(thumbnailAsset()));
        when(storage.get("demo/thumbs/x.jpg")).thenThrow(new StorageException("purgado"));
        when(fetcher.fetch(eq(REMOTE), any(Duration.class)))
                .thenReturn(Optional.of(new Fetched(PNG, "image/png")));

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/png;base64,");
    }

    // ============================ Sin cache → descarga remota ============================

    @Test
    void noCacheDownloadsRemoteEmbedsItAndAutoHeals() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of());
        when(fetcher.fetch(eq(REMOTE), any(Duration.class)))
                .thenReturn(Optional.of(new Fetched(PNG, "image/png")));

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/png;base64,");
        assertThat(thumb.remoteUrl()).isEqualTo(REMOTE);
        // Auto-heal: lo bajado se cachea para no rebajarlo la próxima.
        verify(mediaService).cacheRenderedThumbnail(eq(POST_ID), any(byte[].class), eq("image/png"));
    }

    @Test
    void noCacheRemoteWebpIsTranscodedToPngBeforeEmbedding() throws Exception {
        byte[] webp = Files.readAllBytes(Path.of("src/test/resources/thumbnails/sample.webp"));
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of());
        when(fetcher.fetch(eq(REMOTE), any(Duration.class)))
                .thenReturn(Optional.of(new Fetched(webp, "image/webp")));

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/png;base64,");
        byte[] embedded = Base64.getDecoder()
                .decode(thumb.dataUri().substring("data:image/png;base64,".length()));
        assertThat(ImageIO.read(new ByteArrayInputStream(embedded))).isNotNull();
        // Se cachea ya transcodificado a PNG (PDF-safe), no el WebP original.
        verify(mediaService).cacheRenderedThumbnail(eq(POST_ID), any(byte[].class), eq("image/png"));
    }

    @Test
    void noCacheAndRemoteDeadFallsBackToRemoteUrlOnly() {
        // URL realmente muerta (vencida/timeout): el fetcher devuelve empty → placeholder en el PDF.
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of());
        when(fetcher.fetch(eq(REMOTE), any(Duration.class))).thenReturn(Optional.empty());

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).isNull();
        assertThat(thumb.remoteUrl()).isEqualTo(REMOTE);
        verify(mediaService, never()).cacheRenderedThumbnail(any(), any(), any());
    }

    @Test
    void noCacheAndNoRemoteIsEmpty() {
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of());

        Thumbnail thumb = loader.load(POST_ID, null);

        assertThat(thumb.dataUri()).isNull();
        assertThat(thumb.remoteUrl()).isNull();
        assertThat(thumb.hasImage()).isFalse();
        verify(fetcher, never()).fetch(any(), any());
    }

    @Test
    void autoHealFailureDoesNotBreakTheReport() {
        // Si cachear lo bajado falla, el reporte igual sale con el data-URI (best-effort, no rompe).
        when(mediaAssets.findByPostId(POST_ID)).thenReturn(List.of());
        when(fetcher.fetch(eq(REMOTE), any(Duration.class)))
                .thenReturn(Optional.of(new Fetched(PNG, "image/png")));
        org.mockito.Mockito.doThrow(new RuntimeException("storage caído"))
                .when(mediaService).cacheRenderedThumbnail(any(), any(), any());

        Thumbnail thumb = loader.load(POST_ID, REMOTE);

        assertThat(thumb.dataUri()).startsWith("data:image/png;base64,");
    }
}
