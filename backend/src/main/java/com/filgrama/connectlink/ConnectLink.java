package com.filgrama.connectlink;

import java.time.Instant;

import com.filgrama.domain.enums.Platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Link temporal de onboarding self-service: deja que el cliente conecte su red desde su propio
 * navegador (sin login en Fil-Grama), acotado a un {@code clientId}. <b>No guarda tokens.</b>
 * En DB vive sólo el {@code tokenHash} (sha-256); el raw se devuelve una sola vez al crear.
 * Multi-uso hasta {@code expiresAt}/revocación. Ver spec/02 §connect_links y spec/09 §"Link compartible".
 */
@Entity
@Table(name = "connect_links")
@Getter
@Setter
@NoArgsConstructor
public class ConnectLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clientId;

    /** sha-256 (hex) del token opaco. El raw nunca se persiste. */
    @Column(nullable = false)
    private String tokenHash;

    /** Red fijada por el link; null = el cliente elige. */
    @Enumerated(EnumType.STRING)
    private Platform platform;

    /** Cuenta puntual a reconectar (hereda el guard del open_id esperado); null = connect nuevo. */
    private Long expectedAccountId;

    /** Quién generó el link; pasa a {@code social_accounts.connected_by}. */
    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Desactivación manual; presente ⇒ el link ya no sirve. */
    private Instant revokedAt;

    /** Último uso exitoso (auditoría); el link es multi-uso hasta expirar/revocar. */
    private Instant usedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
