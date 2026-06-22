package com.filgrama.domain;

import com.filgrama.domain.enums.SyncAccountStatus;

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

/** Resultado por cuenta dentro de una corrida del job. */
@Entity
@Table(name = "sync_account_results")
@Getter
@Setter
@NoArgsConstructor
public class SyncAccountResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncAccountStatus status;

    private Integer metricsCaptured;

    @Column(columnDefinition = "text")
    private String errorMessage;
}
