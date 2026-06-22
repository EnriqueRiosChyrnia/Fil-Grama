package com.filgrama.domain;

import java.time.Instant;

import com.filgrama.domain.enums.SyncRunStatus;

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

/** Registro de una corrida del job diario. */
@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncRunStatus status;

    private Integer accountsTotal;

    private Integer accountsOk;

    private Integer accountsFailed;

    @Column(columnDefinition = "text")
    private String errorSummary;
}
