package com.filgrama.reports.render;

import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.media.ImageNormalizer;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.storage.StoragePort;

import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve la miniatura de un post para embeberla en el reporte. Reusa el {@code StoragePort} del
 * track E (lee el binario cacheado) y el repo compartido {@code MediaAssetRepository} (sólo lectura):
 * <ul>
 *   <li>Si hay miniatura cacheada en {@code media_assets} → la baja vía {@code StoragePort.get()} y la
 *       devuelve como {@code data:image/...;base64,...} para incrustarla sin red en el PDF.</li>
 *   <li>Si no hay cache (o el binario no se puede leer) → cae al {@code remote_thumbnail_url} del post
 *       de forma diferida (link/img remoto), sin reimplementar storage.</li>
 * </ul>
 */
@Component
@Slf4j
public class ThumbnailLoader {

    private final MediaAssetRepository mediaAssets;
    private final StoragePort storage;

    public ThumbnailLoader(MediaAssetRepository mediaAssets, StoragePort storage) {
        this.mediaAssets = mediaAssets;
        this.storage = storage;
    }

    /** Miniatura resuelta: {@code dataUri} base64 si hay cache; si no, sólo {@code remoteUrl}. */
    public record Thumbnail(String dataUri, String remoteUrl) {

        static final Thumbnail EMPTY = new Thumbnail(null, null);

        public boolean hasImage() {
            return dataUri != null || remoteUrl != null;
        }
    }

    /**
     * Devuelve la miniatura del post: primero la cacheada (data-URI), si no el remoto diferido.
     *
     * @param postId            id del post.
     * @param remoteThumbnailUrl miniatura remota del post (fallback diferido), puede ser null.
     */
    public Thumbnail load(Long postId, String remoteThumbnailUrl) {
        String remote = remoteThumbnailUrl != null && !remoteThumbnailUrl.isBlank() ? remoteThumbnailUrl : null;

        Optional<MediaAsset> cached = mediaAssets.findByPostId(postId).stream()
                .filter(a -> a.getKind() == MediaKind.THUMBNAIL)
                .findFirst();
        if (cached.isPresent()) {
            String dataUri = toDataUri(cached.get());
            if (dataUri != null) {
                return new Thumbnail(dataUri, remote);
            }
        }
        return remote == null ? Thumbnail.EMPTY : new Thumbnail(null, remote);
    }

    private String toDataUri(MediaAsset asset) {
        try {
            byte[] bytes = storage.get(asset.getStoragePath());
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            // El PDF (openhtmltopdf/PDFBox vía ImageIO) no rasteriza WebP — la miniatura cacheada de
            // TikTok viene en WebP. Normalizamos a un formato PDF-safe (transcodea WebP -> PNG) antes
            // de embeber; si los bytes no son una imagen decodificable, caemos al remoto (null).
            return ImageNormalizer.toPdfSafe(bytes, asset.getContentType())
                    .map(img -> "data:" + img.contentType() + ";base64,"
                            + Base64.getEncoder().encodeToString(img.bytes()))
                    .orElse(null);
        } catch (RuntimeException e) {
            // La miniatura es accesoria: ante CUALQUIER fallo de storage (objeto purgado →
            // StorageException, o backend caído → otra RuntimeException sin mapear) caemos al remoto
            // sin romper el reporte. assemble() corre antes del try/catch del service: si esto se
            // propagara, terminaría en un 500 crudo.
            log.warn("No se pudo leer la miniatura {} del storage: {}", asset.getStoragePath(), e.toString());
            return null;
        }
    }
}
