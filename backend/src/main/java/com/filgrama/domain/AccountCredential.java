package com.filgrama.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Token OAuth de larga duración. 1:1 con SocialAccount (PK = account_id). */
@Entity
@Table(name = "account_credentials")
@Getter
@Setter
@NoArgsConstructor
public class AccountCredential {

    @Id
    private Long accountId;

    /** Token de acceso cifrado a nivel app. */
    @Column(nullable = false)
    private byte[] accessTokenEnc;

    /** Refresh token cifrado (si la red lo provee). */
    private byte[] refreshTokenEnc;

    private String tokenType;

    private String scopes;

    private Instant expiresAt;

    private Instant lastRefreshedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
