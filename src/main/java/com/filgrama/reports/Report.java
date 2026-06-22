package com.filgrama.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * Recurso reporte (tabla {@code reports}, migración V4 — dueña del track G). Persiste los metadatos
 * del reporte generado; el archivo en sí vive en object storage (vía {@code StoragePort} del track E)
 * y acá se guarda sólo su {@code storage_path}. Multi-tenant: siempre cuelga de {@code client_id}.
 *
 * <p>Los campos {@code narrative*} son la narrativa IA de v2: nullable desde ya (en v1 nunca se
 * setean) para no volver a migrar cuando se construya el MCP. Ver spec/08.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    /** Redes incluidas, ej. {@code ["INSTAGRAM","TIKTOK"]}. Mapea a la columna {@code jsonb}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> platforms;

    /** Métrica (lógica o metric_key) usada para ordenar las destacadas. */
    private String rankBy;

    /** Ruta del archivo generado en el storage (key {@code reports/{clientId}/{reportId}.{ext}}). */
    private String storagePath;

    // ---- Narrativa IA (v2; nullable, nunca se setea en v1) ----
    @Column(columnDefinition = "text")
    private String narrativeMd;

    private String narrativeSource;

    private String narrativeModel;

    private Instant narrativeGeneratedAt;

    /** Usuario autenticado que generó el reporte (del SecurityContext). */
    private Long createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
