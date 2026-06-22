package com.filgrama.media;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.MediaAsset;

/**
 * Consultas extra sobre {@code media_assets} que el track Storage necesita y que no están en el
 * repo compartido {@code com.filgrama.repository.MediaAssetRepository} (que no se edita). Vive en
 * el paquete dueño del track. Misma entidad/tabla.
 */
public interface MediaAssetQueryRepository extends JpaRepository<MediaAsset, Long> {

    /** Miniaturas vencidas por retención ({@code purge_after < ahora}). */
    List<MediaAsset> findByPurgeAfterBefore(Instant cutoff);
}
