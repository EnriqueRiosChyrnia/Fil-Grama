package com.filgrama.reports;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.filgrama.domain.Client;
import com.filgrama.error.ApiException;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportDataAssembler;
import com.filgrama.reports.render.MarkdownRenderer;
import com.filgrama.reports.render.PdfRenderer;
import com.filgrama.reports.web.GenerateReportRequest;
import com.filgrama.reports.web.PreviewReportRequest;
import com.filgrama.repository.ClientRepository;
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
    private final ReportNarrativeService narrativeService;
    private final MarkdownRenderer markdownRenderer;
    private final PdfRenderer pdfRenderer;
    private final StoragePort storage;
    private final ClientRepository clientRepository;

    public ReportService(ReportRepository reportRepository,
                         ReportDataAssembler assembler,
                         ReportNarrativeService narrativeService,
                         MarkdownRenderer markdownRenderer,
                         PdfRenderer pdfRenderer,
                         StoragePort storage,
                         ClientRepository clientRepository) {
        this.reportRepository = reportRepository;
        this.assembler = assembler;
        this.narrativeService = narrativeService;
        this.markdownRenderer = markdownRenderer;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.clientRepository = clientRepository;
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
        ReportData data = buildReportData(clientId, request.reportType(), request.format(),
                request.from(), request.to(), request.platforms(), request.accountIds(), request.rankBy());

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

    /**
     * Arma el {@link ReportData} (CU5) tolerando que {@code assemble} corra ANTES del try/catch de la
     * generación: las validaciones de negocio ({@link ApiException} 404 cliente / 400 rango / 422
     * {@code rankBy}) se propagan tal cual, pero <b>cualquier otra</b> {@code RuntimeException}
     * inesperada (p. ej. storage caído al resolver una miniatura) se mapea a una {@link ApiException}
     * controlada en vez de filtrarse como 500 crudo. Punto único reusado por {@link #generate} y por
     * la vista previa ({@code :preview}) → ambos salen del MISMO armado, sin divergencias.
     */
    public ReportData buildReportData(Long clientId, ReportType reportType, ReportFormat format,
                                      LocalDate from, LocalDate to, List<String> platforms,
                                      List<Long> accountIds, String rankBy) {
        try {
            ReportData data = assembler.assemble(clientId, reportType, format, from, to, platforms, accountIds, rankBy);
            // Inyecta la narrativa persistida del período (si la hay). Sin narrativa → sale igual que
            // hoy (spec/08). Alimenta tanto la vista (:preview) como el export (PDF/MD) sin divergir.
            String narrative = narrativeService.findNarrative(clientId, from, to);
            return narrative == null ? data : data.withNarrative(narrative);
        } catch (ApiException e) {
            throw e; // 404/400/422 de validación: contrato, pasan sin tocar.
        } catch (RuntimeException e) {
            log.warn("No se pudo armar el ReportData del cliente {}: {}", clientId, e.toString());
            throw ApiException.unprocessable("No se pudo armar el reporte para el cliente %d".formatted(clientId));
        }
    }

    /**
     * Vista previa: arma y devuelve el {@link ReportData} (mismos números que el export) <b>sin</b>
     * renderizar, guardar archivo ni persistir fila. {@code format} no aplica (no hay export) → null.
     * Mismas validaciones que {@link #generate} (cliente 404, rango 400, {@code rankBy} 422) porque
     * reusa el MISMO {@link #buildReportData}; rango sin datos → estructura vacía amable (no error).
     */
    public ReportData preview(Long clientId, PreviewReportRequest request) {
        return buildReportData(clientId, request.reportType(), null,
                request.from(), request.to(), request.platforms(), request.accountIds(), request.rankBy());
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
        return new DownloadPayload(content, report.getFormat().contentType(), downloadFilename(clientId, report));
    }

    /**
     * Nombre de archivo descriptivo de la descarga: {@code reporte-<cliente>-<tipo>-<from>_a_<to>.<ext>}
     * (ej. {@code reporte-tiktok-prueba-summary-2026-03-27_a_2026-06-24.pdf}). El slug del nombre del
     * cliente lo hace reconocible en la carpeta de Descargas; el título DENTRO del reporte ya trae el
     * nombre completo. Las fechas son ISO ({@code yyyy-MM-dd}) del rango persistido en la fila.
     */
    private String downloadFilename(Long clientId, Report report) {
        String clientName = clientRepository.findById(clientId).map(Client::getName).orElse(null);
        return "reporte-%s-%s-%s_a_%s.%s".formatted(
                slug(clientName),
                report.getReportType().name().toLowerCase(Locale.ROOT),
                report.getPeriodFrom(),
                report.getPeriodTo(),
                report.getFormat().extension());
    }

    /** Slug ASCII seguro para nombre de archivo: minúsculas, sin acentos, separadores → {@code -}. */
    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "cliente";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "cliente" : slug;
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
