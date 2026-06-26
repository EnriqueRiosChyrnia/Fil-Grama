package com.filgrama.reports.render;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.media.ImageNormalizer;
import com.filgrama.media.MediaService;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.storage.StoragePort;
import com.filgrama.sync.capture.ThumbnailFetcher;
import com.filgrama.sync.capture.ThumbnailFetcher.Fetched;

import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve la miniatura de un post para embeberla en el reporte. El motor de PDF sólo rasteriza
 * {@code data:image/...;base64,...} (no hace red al renderizar), así que esta clase devuelve siempre
 * un data-URI cuando logra una imagen. Estrategia en cascada:
 * <ol>
 *   <li><b>Cache</b>: si hay miniatura en {@code media_assets} → la baja vía {@code StoragePort.get()},
 *       la deja PDF-safe (WebP → PNG con {@link ImageNormalizer}) y la devuelve como data-URI.</li>
 *   <li><b>Fallback remoto</b>: si no hay cache utilizable y el post tiene {@code remote_thumbnail_url}
 *       → la baja server-side reusando el {@link ThumbnailFetcher} (mismo cliente HTTP/transcode que el
 *       sync) con un timeout corto, la transcodifica y la devuelve como data-URI. <b>Auto-heal</b>: lo
 *       que baja lo cachea ({@link MediaService#cacheRenderedThumbnail}) para no rebajarlo la próxima.</li>
 *   <li><b>Placeholder</b>: si todo falla (URL vencida / timeout / no decodifica) → data-URI null. La
 *       miniatura es accesoria: cualquier fallo cae a placeholder, nunca rompe el reporte.</li>
 * </ol>
 */
@Component
@Slf4j
public class ThumbnailLoader {

    private final MediaAssetRepository mediaAssets;
    private final StoragePort storage;
    private final ThumbnailFetcher fetcher;
    private final MediaService mediaService;
    /** Timeout corto del fallback remoto: protege la performance del EXTENDED (varias publicaciones). */
    private final Duration renderTimeout;

    public ThumbnailLoader(MediaAssetRepository mediaAssets, StoragePort storage,
                           ThumbnailFetcher fetcher, MediaService mediaService,
                           @Value("${reports.thumbnail.render-timeout-seconds:4}") long renderTimeoutSeconds) {
        this.mediaAssets = mediaAssets;
        this.storage = storage;
        this.fetcher = fetcher;
        this.mediaService = mediaService;
        this.renderTimeout = Duration.ofSeconds(renderTimeoutSeconds);
    }

    /** Miniatura resuelta: {@code dataUri} base64 para el PDF; {@code remoteUrl} para la web/diferido. */
    public record Thumbnail(String dataUri, String remoteUrl) {

        static final Thumbnail EMPTY = new Thumbnail(null, null);

        public boolean hasImage() {
            return dataUri != null || remoteUrl != null;
        }
    }

    /**
     * Devuelve la miniatura del post: cache (data-URI) → fallback remoto (data-URI, best-effort) →
     * sólo el remoto diferido si ni siquiera se pudo bajar.
     *
     * @param postId             id del post.
     * @param remoteThumbnailUrl miniatura remota del post (fallback), puede ser null.
     */
    public Thumbnail load(Long postId, String remoteThumbnailUrl) {
        String remote = remoteThumbnailUrl != null && !remoteThumbnailUrl.isBlank() ? remoteThumbnailUrl : null;

        Optional<MediaAsset> cached = mediaAssets.findByPostId(postId).stream()
                .filter(a -> a.getKind() == MediaKind.THUMBNAIL)
                .findFirst();
        if (cached.isPresent()) {
            String dataUri = cachedDataUri(cached.get());
            if (dataUri != null) {
                return new Thumbnail(dataUri, remote);
            }
        }

        // Sin cache utilizable (nunca se cacheó, o el binario no se pudo leer): bajamos el remoto al
        // vuelo. Es lo que la web ya hace en el cliente; acá lo hacemos server-side para el PDF.
        if (remote != null) {
            String dataUri = fetchRemoteDataUri(postId, remote);
            if (dataUri != null) {
                return new Thumbnail(dataUri, remote);
            }
        }

        return remote == null ? Thumbnail.EMPTY : new Thumbnail(null, remote);
    }

    /** Lee el binario cacheado y lo deja como data-URI PDF-safe; null si no se puede leer/decodificar. */
    private String cachedDataUri(MediaAsset asset) {
        try {
            return toDataUri(storage.get(asset.getStoragePath()), asset.getContentType());
        } catch (RuntimeException e) {
            // La miniatura es accesoria: ante CUALQUIER fallo de storage (objeto purgado →
            // StorageException, o backend caído → otra RuntimeException sin mapear) caemos al fallback
            // remoto sin romper el reporte. assemble() corre antes del try/catch del service: si esto se
            // propagara, terminaría en un 500 crudo.
            log.warn("No se pudo leer la miniatura {} del storage: {}", asset.getStoragePath(), e.toString());
            return null;
        }
    }

    /**
     * Baja la miniatura remota al vuelo (timeout corto), la transcodifica a PDF-safe y, best-effort, la
     * cachea (auto-heal). Cualquier fallo → null (cae a placeholder). El {@link ThumbnailFetcher} ya es
     * best-effort (no lanza), pero envolvemos por las dudas: nada acá puede romper el reporte.
     */
    private String fetchRemoteDataUri(Long postId, String remoteUrl) {
        Optional<Fetched> fetched;
        try {
            fetched = fetcher.fetch(remoteUrl, renderTimeout);
        } catch (RuntimeException e) {
            log.warn("Fallback de miniatura del post {}: error al bajar el remoto: {}", postId, e.toString());
            return null;
        }
        if (fetched.isEmpty()) {
            return null;
        }
        Fetched f = fetched.get();
        // El fetcher real ya entrega bytes PDF-safe; igual normalizamos para unificar con la rama de
        // cache y filtrar bytes no-imagen (p.ej. el fetcher mock de local/test).
        Optional<ImageNormalizer.Image> pdfSafe = ImageNormalizer.toPdfSafe(f.bytes(), f.contentType());
        if (pdfSafe.isEmpty()) {
            return null;
        }
        ImageNormalizer.Image image = pdfSafe.get();
        cacheQuietly(postId, image);
        return "data:" + image.contentType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes());
    }

    /** Auto-heal: persiste lo recién bajado para no rebajarlo la próxima. Best-effort: nunca rompe. */
    private void cacheQuietly(Long postId, ImageNormalizer.Image image) {
        try {
            mediaService.cacheRenderedThumbnail(postId, image.bytes(), image.contentType());
        } catch (RuntimeException e) {
            log.warn("Auto-heal: no se pudo cachear la miniatura del post {}: {}", postId, e.toString());
        }
    }

    /** Bytes (cacheados o recién bajados) → data-URI PDF-safe (WebP → PNG); null si están vacíos o no decodifican. */
    private static String toDataUri(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        // El PDF (openhtmltopdf/PDFBox vía ImageIO) no rasteriza WebP — la miniatura de TikTok viene en
        // WebP. Normalizamos a un formato PDF-safe (transcodea WebP -> PNG) antes de embeber; si los
        // bytes no son una imagen decodificable, devolvemos null (el llamador decide el fallback).
        return ImageNormalizer.toPdfSafe(bytes, declaredContentType)
                .map(img -> "data:" + img.contentType() + ";base64,"
                        + Base64.getEncoder().encodeToString(img.bytes()))
                .orElse(null);
    }
}
