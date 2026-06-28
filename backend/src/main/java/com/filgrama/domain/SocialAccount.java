package com.filgrama.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;

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
@Table(name = "social_accounts")
@Getter
@Setter
@NoArgsConstructor
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private String externalAccountId;

    /**
     * Id del usuario Meta que autorizó (del {@code /me} de Graph). Lo necesitan los callbacks de
     * Deauthorize / Data Deletion de Meta para ubicar las filas por usuario (Meta manda este id, no
     * el de Página/IG). Se denormaliza igual en cada fila hermana del mismo consentimiento. {@code null}
     * en TikTok y filas previas a V7. spec/09 §Meta + §Ciclo de vida.
     */
    private String metaUserId;

    private String handle;

    private String displayName;

    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    /** JSON con las métricas/endpoints que la cuenta soporta hoy. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String capabilities;

    private Instant capabilitiesCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.CONNECTED;

    private Long connectedBy;

    @Column(nullable = false, updatable = false)
    private Instant connectedAt;
}
