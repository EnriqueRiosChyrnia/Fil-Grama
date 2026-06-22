package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request de {@code POST /metrics:batchReport} (espejo de GA4 {@code batchRunReports}): varios
 * informes en una llamada. Máx. 20 requests (400 si se excede).
 */
public record BatchReportRequest(
        @Schema(description = "1..20 informes de cuenta. Se procesan en orden; la response los devuelve "
                + "en el mismo orden. Un accountId inexistente hace fallar todo el batch (404).")
        List<AccountReportRequest> requests) {
}
