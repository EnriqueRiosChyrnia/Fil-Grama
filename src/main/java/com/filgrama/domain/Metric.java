package com.filgrama.domain;

import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.MetricState;
import com.filgrama.domain.enums.MetricTier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo de métricas. PK = key (ej. ig_reach). */
@Entity
@Table(name = "metrics")
@Getter
@Setter
@NoArgsConstructor
public class Metric {

    @Id
    @Column(name = "key")
    private String key;

    @Column(nullable = false)
    private String displayName;

    /** NULL = aplica a todas las redes. */
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricLevel level;

    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricTier tier = MetricTier.CORE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricState state = MetricState.ACTIVE;

    private String description;
}
