package com.filgrama.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Snapshot append-only de una métrica de post. Idempotencia diaria por capture_date. */
@Entity
@Table(name = "post_metric_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class PostMetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private String metricKey;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private LocalDate captureDate;
}
