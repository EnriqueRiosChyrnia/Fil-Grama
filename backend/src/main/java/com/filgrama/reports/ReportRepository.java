package com.filgrama.reports;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistencia del recurso {@code reports}. Vive en el paquete dueño del track (no toca
 * {@code com.filgrama.repository}). El lookup multi-tenant exige el {@code clientId} para que un
 * reporte de otro cliente devuelva 404 (no fuga entre tenants).
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Reporte por id que además pertenezca al cliente (multi-tenant). */
    Optional<Report> findByIdAndClientId(Long id, Long clientId);

    /**
     * Reporte más reciente del cliente para ese período exacto (con o sin narrativa). Lo usa
     * {@code save_report_narrative} del MCP para adjuntar la narrativa a un reporte ya generado del
     * período; si no hay ninguno, crea uno nuevo (spec/08).
     */
    Optional<Report> findFirstByClientIdAndPeriodFromAndPeriodToOrderByCreatedAtDesc(
            Long clientId, LocalDate periodFrom, LocalDate periodTo);

    /**
     * Narrativa vigente del período: el reporte más reciente CON narrativa para ese cliente+período.
     * Alimenta la sección "Análisis del mes" de la vista/PDF (sin narrativa, sale igual que hoy).
     */
    Optional<Report> findFirstByClientIdAndPeriodFromAndPeriodToAndNarrativeMdIsNotNullOrderByNarrativeGeneratedAtDesc(
            Long clientId, LocalDate periodFrom, LocalDate periodTo);
}
