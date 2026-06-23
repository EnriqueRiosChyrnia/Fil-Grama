package com.filgrama.reports.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.reports.ReportService;
import com.filgrama.reports.data.ReportData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * {@code POST /api/v1/clients/{clientId}/reports:preview} — vista previa del reporte (custom method
 * AIP-136). Devuelve el {@link ReportData} (los MISMOS números que usa el renderer del export) <b>sin
 * generar ni persistir archivo</b>: el front lo usa para la vista en pantalla, y el {@code POST
 * /reports} para exportar el PDF/MD → preview y export salen del mismo armado y no divergen.
 *
 * <p>Controller propio (no cuelga del {@code /reports} de {@link ReportController}) para que el literal
 * {@code reports:preview} sea un segmento exacto, sin {@code /} espurio — mismo patrón que los
 * {@code metrics:report}/{@code metrics:batchReport} del track Métricas. Multi-tenant: el
 * {@code clientId} de la ruta filtra todo (cliente inexistente → 404). Requiere auth (SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}")
@Tag(name = "Reportes")
public class ReportPreviewController {

    private final ReportService reportService;

    public ReportPreviewController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Vista previa del reporte (datos, sin archivo)",
            description = "Devuelve el ReportData que consume el renderer (Period, KPIs por red, evolución "
                    + "de alcance, publicaciones agrupadas, destacadas) para pintar la vista en pantalla. "
                    + "Mismo armado que el export → no divergen. Cliente inexistente → 404; rango inválido → "
                    + "400; rankBy desconocido → 422; rango sin datos → estructura vacía amable (no error).")
    @PostMapping("/reports:preview")
    public ReportData preview(@PathVariable Long clientId, @Valid @RequestBody PreviewReportRequest request) {
        return reportService.preview(clientId, request);
    }
}
