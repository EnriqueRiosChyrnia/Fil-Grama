package com.filgrama.domain;

import java.time.Instant;

import com.filgrama.domain.enums.MediaKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Miniatura cacheada (solo binarios livianos). El binario vive en object storage; acá la ruta. */
@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(nullable = false)
    private String storagePath;

    private String contentType;

    private Integer bytes;

    @Column(nullable = false)
    private Instant capturedAt;

    private Instant purgeAfter;
}
