package com.filgrama.auth.token;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token persistido y rotado (mapa la tabla {@code refresh_tokens} de V2).
 * Vive en {@code com.filgrama.auth} (NO en {@code com.filgrama.domain}).
 *
 * <p>Se guarda el <b>hash</b> del token, nunca el valor en claro. Las filas con
 * misma {@code familyId} forman una familia de rotación (para detección de reuso).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** NULL = vigente. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** id del token que reemplazó a éste al rotar. */
    @Column(name = "replaced_by")
    private Long replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
