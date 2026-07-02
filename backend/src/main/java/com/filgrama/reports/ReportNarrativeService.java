package com.filgrama.reports;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.error.ApiException;

/**
 * Persistencia y lectura de la narrativa "Análisis del mes" (spec/08). Es la única puerta de ESCRITURA
 * de la narrativa: la usa la tool MCP {@code save_report_narrative} (la única de escritura del MCP) y
 * la de LECTURA la usa {@link ReportService} para inyectar la narrativa del período en el
 * {@link com.filgrama.reports.data.ReportData} que consumen pantalla y PDF. Sin narrativa, el reporte
 * sale igual que hoy (principio rector de spec/08).
 *
 * <p>Los 4 campos {@code narrative_*} viven en la tabla {@code reports} desde la migración V4 (ya
 * nullable), por eso T5 no agrega migración: solo empieza a poblarlos y a leerlos.
 */
@Service
public class ReportNarrativeService {

    private final ReportRepository reports;

    public ReportNarrativeService(ReportRepository reports) {
        this.reports = reports;
    }

    /** Resultado de guardar: el reporte afectado y si hubo que crearlo (no existía uno del período). */
    public record SaveResult(Report report, boolean createdNewReport) {
    }

    /** Narrativa vigente del período (markdown) o {@code null} si todavía no se guardó ninguna. */
    @Transactional(readOnly = true)
    public String findNarrative(Long clientId, LocalDate from, LocalDate to) {
        return reports
                .findFirstByClientIdAndPeriodFromAndPeriodToAndNarrativeMdIsNotNullOrderByNarrativeGeneratedAtDesc(
                        clientId, from, to)
                .map(Report::getNarrativeMd)
                .orElse(null);
    }

    /**
     * Guarda la narrativa del período asociándola a un reporte: si ya hay uno de ese período lo
     * actualiza; si no, crea uno nuevo (holder sin archivo exportado — spec/08 "crearlo si no existe").
     * {@code source} ∈ {@code MCP|API|MANUAL} (constraint de V4); en T5 siempre {@code MCP}.
     */
    @Transactional
    public SaveResult save(Long clientId, LocalDate from, LocalDate to, String markdown,
                           String source, String model, Long createdBy) {
        if (markdown == null || markdown.isBlank()) {
            throw ApiException.badRequest("La narrativa (markdown) no puede estar vacía");
        }
        Report report = reports
                .findFirstByClientIdAndPeriodFromAndPeriodToOrderByCreatedAtDesc(clientId, from, to)
                .orElse(null);
        boolean created = report == null;
        if (created) {
            report = new Report();
            report.setClientId(clientId);
            // Holder de narrativa: sin archivo exportado (storagePath null → la descarga responde 404).
            report.setReportType(ReportType.EXTENDED);
            report.setFormat(ReportFormat.PDF);
            report.setStatus(ReportStatus.COMPLETED);
            report.setPeriodFrom(from);
            report.setPeriodTo(to);
            report.setCreatedBy(createdBy);
        }
        report.setNarrativeMd(markdown.strip());
        report.setNarrativeSource(source);
        report.setNarrativeModel(model);
        report.setNarrativeGeneratedAt(Instant.now());
        return new SaveResult(reports.save(report), created);
    }
}
