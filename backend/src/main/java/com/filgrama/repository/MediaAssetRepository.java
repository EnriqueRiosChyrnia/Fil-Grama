package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.filgrama.domain.MediaAsset;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByPostId(Long postId);

    /**
     * Borrado real de las miniaturas de los posts de una cuenta (compliance Meta: data-deletion).
     * {@code media_assets} no denormaliza {@code account_id}: se filtra por los posts de la cuenta.
     */
    @Modifying
    @Query("delete from MediaAsset m where m.postId in (select p.id from Post p where p.accountId = ?1)")
    void deleteByAccountId(Long accountId);
}
