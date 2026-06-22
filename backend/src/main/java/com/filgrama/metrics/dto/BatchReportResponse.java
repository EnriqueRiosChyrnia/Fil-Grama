package com.filgrama.metrics.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response de {@code POST /metrics:batchReport}: un informe por request, en el mismo orden. */
public record BatchReportResponse(
        @Schema(description = "Informes en el mismo orden que 'requests'.")
        List<AccountReportResponse> reports) {
}
