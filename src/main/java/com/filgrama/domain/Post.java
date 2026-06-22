package com.filgrama.domain;

import java.time.Instant;

import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;

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

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private String externalPostId;

    @Enumerated(EnumType.STRING)
    private PostType postType;

    private String permalink;

    @Column(columnDefinition = "text")
    private String caption;

    private String remoteMediaUrl;

    private String remoteThumbnailUrl;

    @Column(name = "is_ephemeral", nullable = false)
    private boolean isEphemeral = false;

    private Instant publishedAt;

    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant firstSeenAt;
}
