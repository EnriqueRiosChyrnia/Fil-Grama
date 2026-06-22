package com.filgrama.reports;

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
}
