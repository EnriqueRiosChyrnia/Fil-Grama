package com.filgrama.domain;

import java.time.Instant;

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
 * Pedido de borrado de datos de Meta (callback Data Deletion request). Persiste el
 * {@code confirmation_code} único que se devuelve a Meta y al usuario para consultar el estado por
 * {@code status_url}. spec/09 §Meta · tracks/FG-META-backend.md Fase 1.
 */
@Entity
@Table(name = "data_deletion_requests")
@Getter
@Setter
@NoArgsConstructor
public class DataDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código de alta entropía único; se devuelve a Meta y se usa en la {@code status_url}. */
    @Column(nullable = false)
    private String confirmationCode;

    /** Usuario Meta del {@code signed_request} cuyos datos se borraron. */
    @Column(nullable = false)
    private String metaUserId;

    /** {@code RECEIVED} | {@code COMPLETED} (el borrado es síncrono ⇒ COMPLETED). */
    @Column(nullable = false)
    private String status;

    /** Cuántas cuentas Meta se dieron de baja por este pedido. */
    @Column(nullable = false)
    private int accountsRemoved;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    private Instant completedAt;
}
