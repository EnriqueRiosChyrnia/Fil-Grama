package com.filgrama.media;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.MediaAsset;
import com.filgrama.domain.Post;
import com.filgrama.domain.enums.MediaKind;
import com.filgrama.error.ApiException;
import com.filgrama.repository.MediaAssetRepository;
import com.filgrama.repository.PostRepository;
import com.filgrama.storage.StorageException;
import com.filgrama.storage.StoragePort;
import com.filgrama.storage.StoredObject;

import lombok.extern.slf4j.Slf4j;

/**
 * Caché de miniaturas: sube el binario detrás del {@link StoragePort} y persiste solo la ruta +
 * metadata en {@code media_assets} (el binario nunca va a Postgres). Pensado sobre todo para
 * <b>stories</b>, cuyo media en la red desaparece a las 24h.
 *
 * <p>No descarga media de la red real (eso es del job, track F): recibe los bytes ya obtenidos.
 */
@Service
@Slf4j
public class MediaService {

    /** TTL de la URL presigned para servir la miniatura al front. */
    static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    /** Retención de miniaturas de stories: se purgan {@code now + N días}. */
    static final Duration STORY_RETENTION = Duration.ofDays(30);

    private final StoragePort storage;
    private final MediaAssetRepository mediaAssets;
    private final MediaAssetQueryRepository mediaAssetQuery;
    private final PostRepository posts;

    public MediaService(StoragePort storage, MediaAssetRepository mediaAssets,
                        MediaAssetQueryRepository mediaAssetQuery, PostRepository posts) {
        this.storage = storage;
        this.mediaAssets = mediaAssets;
        this.mediaAssetQuery = mediaAssetQuery;
        this.posts = posts;
    }

    /**
     * Sube la miniatura al storage y crea la fila {@code media_assets} (kind=THUMBNAIL). Para
     * stories (post efímero) setea {@code purge_after = now + STORY_RETENTION}.
     *
     * @return el {@link MediaAsset} persistido.
     */
    @Transactional
    public MediaAsset cacheThumbnail(Post post, byte[] imageBytes, String contentType) {
        if (post == null || post.getId() == null) {
            throw ApiException.unprocessable("El post debe estar persistido para cachear su miniatura");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw ApiException.unprocessable("La miniatura no puede estar vacía");
        }

        String key = thumbnailKey(post, contentType);
        StoredObject stored;
        try {
            stored = storage.put(key, imageBytes, contentType);
        } catch (StorageException e) {
            // No exponemos el error crudo de S3/IO; lo traducimos a un 422 controlado vía el handler.
            log.warn("Fallo al subir miniatura del post {}: {}", post.getId(), e.getMessage());
            throw ApiException.unprocessable("No se pudo almacenar la miniatura");
        }

        Instant now = Instant.now();
        MediaAsset asset = new MediaAsset();
        asset.setPostId(post.getId());
        asset.setClientId(post.getClientId());
        asset.setKind(MediaKind.THUMBNAIL);
        asset.setStoragePath(stored.storagePath());
        asset.setContentType(contentType);
        asset.setBytes(imageBytes.length);
        asset.setCapturedAt(now);
        if (post.isEphemeral()) {
            asset.setPurgeAfter(now.plus(STORY_RETENTION));
        }
        return mediaAssets.save(asset);
    }

    /**
     * URL para mostrar la miniatura: presigned si el backend lo soporta; si no, cae al
     * {@code remote_thumbnail_url} del post. v1 simple.
     */
    @Transactional(readOnly = true)
    public Optional<String> getThumbnailUrl(MediaAsset asset) {
        Optional<String> presigned = storage.presignedUrl(asset.getStoragePath(), PRESIGN_TTL);
        if (presigned.isPresent()) {
            return presigned;
        }
        return posts.findById(asset.getPostId())
                .map(Post::getRemoteThumbnailUrl)
                .filter(url -> url != null && !url.isBlank());
    }

    /**
     * Purga miniaturas vencidas ({@code purge_after < now}): borra el binario del storage y la fila.
     * No se agenda acá (el scheduling es del job/track F); se expone el método y listo.
     *
     * @return cantidad de filas purgadas.
     */
    @Transactional
    public int purgeExpired() {
        var expired = mediaAssetQuery.findByPurgeAfterBefore(Instant.now());
        for (MediaAsset asset : expired) {
            try {
                storage.delete(asset.getStoragePath());
            } catch (StorageException e) {
                // El binario puede ya no existir; igual limpiamos la fila para no reintentar infinito.
                log.warn("No se pudo borrar el binario {} al purgar: {}",
                        asset.getStoragePath(), e.getMessage());
            }
            mediaAssets.delete(asset);
        }
        return expired.size();
    }

    /** Key de la miniatura: {@code clients/{clientId}/posts/{postId}/thumb-{captured_date}.{ext}}. */
    private String thumbnailKey(Post post, String contentType) {
        LocalDate capturedDate = LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
        return "clients/%d/posts/%d/thumb-%s.%s".formatted(
                post.getClientId(), post.getId(), capturedDate, extensionFor(contentType));
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return "bin";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }
}
