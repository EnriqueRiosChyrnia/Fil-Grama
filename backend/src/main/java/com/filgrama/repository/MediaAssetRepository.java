package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.MediaAsset;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByPostId(Long postId);
}
