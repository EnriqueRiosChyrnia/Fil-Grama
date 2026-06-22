package com.filgrama.reports;

import org.springframework.stereotype.Service;

import com.filgrama.error.ApiException;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportDataAssembler;
import com.filgrama.reports.render.MarkdownRenderer;
import com.filgrama.reports.render.PdfRenderer;
import com.filgrama.reports.web.GenerateReportRequest;
import com.filgrama.storage.StorageException;
import com.filgrama.storage.StoragePort;
import com.filgrama.storage.StoredObject;

import lombok.extern.slf4j.Slf4j;

/**
 * Orquesta la generación síncrona del reporte (CU5): valida y arma el {@link ReportData} (servicios
 * del track D + queries propias), lo renderiza según el formato, <b>guarda el archivo vía el
 * {@code StoragePort} del track E</b> y persiste la fila en {@code reports}. Si todo va bien el
 * reporte queda {@code COMPLETED} con su {@code storage_path}; si la generación falla, queda
 * {@code FAILED} y el error sale por el handler compartido. Multi-tenant: todo filtra por
 * {@code client_id}; un reporte de otro cliente da 404.
 */
@Service
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportDataAssembler assembler;
    private final MarkdownRenderer markdownRenderer;
    private final PdfRenderer pdfRenderer;
    private final StoragePort storage;

    public ReportService(ReportRepository reportRepository,
                         ReportDataAssembler assembler,
                         MarkdownRenderer markdownRenderer,
                         PdfRenderer pdfRenderer,
                         StoragePort storage) {
        this.reportRepository = reportRepository;
        this.assembler = assembler;
        this.markdownRenderer = markdownRenderer;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
    }

    /** Archivo listo para servir en la descarga. */
    public record DownloadPayload(byte[] content, String contentType, String filename) {
    }

    /**
     * Genera el reporte de forma síncrona y devuelve el recurso persistido. La validación de cliente
     * (404), rango (400) y {@code rankBy} (422) ocurre al armar el {@link ReportData}, antes de crear
     * la fila — así un pedido inválido no deja un reporte fantasma.
     */
    public Report generate(Long clientId, GenerateReportRequest request, Long userId) {
        ReportData data = assembler.assemble(clientId, request.reportType(), request.format(),
                request.from(), request.to(), request.platforms(), request.rankBy());

        Report report = new Report();
        report.setClientId(clientId);
        report.setReportType(request.reportType());
        report.setFormat(request.format());
        report.setStatus(ReportStatus.PENDING);
        report.setPeriodFrom(request.from());
        report.setPeriodTo(request.to());
        report.setPlatforms(data.platforms());
        report.setRankBy(data.rankBy());
        report.setCreatedBy(userId);
        report = reportRepository.save(report);

        try {
            byte[] content = renderContent(request.format(), data);
            String key = storageKey(clientId, report.getId(), request.format());
            StoredObject stored = storage.put(key, content, request.format().contentType());
            report.setStoragePath(stored.storagePath());
            report.setStatus(ReportStatus.COMPLETED);
            return reportRepository.save(report);
        } catch (RuntimeException e) {
            report.setStatus(ReportStatus.FAILED);
            reportRepository.save(report);
            log.warn("Reporte {} del cliente {} quedó FAILED: {}", report.getId(), clientId, e.toString());
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.unprocessable("No se pudo generar el reporte");
        }
    }

    /** Metadatos del reporte del cliente (404 si no existe o es de otro cliente). */
    public Report get(Long clientId, Long reportId) {
        return reportRepository.findByIdAndClientId(reportId, clientId)
                .orElseThrow(() -> ApiException.notFound(
                        "Report %d not found for client %d".formatted(reportId, clientId)));
    }

    /** Descarga el archivo generado vía el {@code StoragePort} (stream de bytes + headers). */
    public DownloadPayload download(Long clientId, Long reportId) {
        Report report = get(clientId, reportId);
        if (report.getStatus() != ReportStatus.COMPLETED || report.getStoragePath() == null) {
            throw ApiException.notFound("El reporte %d no tiene archivo disponible".formatted(reportId));
        }
        byte[] content;
        try {
            content = storage.get(report.getStoragePath());
        } catch (StorageException e) {
            log.warn("No se pudo leer el archivo del reporte {}: {}", reportId, e.getMessage());
            throw ApiException.notFound("El archivo del reporte %d no está disponible".formatted(reportId));
        }
        String filename = "reporte-%d.%s".formatted(reportId, report.getFormat().extension());
        return new DownloadPayload(content, report.getFormat().contentType(), filename);
    }

    private byte[] renderContent(ReportFormat format, ReportData data) {
        return switch (format) {
            case MARKDOWN -> markdownRenderer.render(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case PDF -> pdfRenderer.render(data);
        };
    }

    private static String storageKey(Long clientId, Long reportId, ReportFormat format) {
        return "reports/%d/%d.%s".formatted(clientId, reportId, format.extension());
    }
}
