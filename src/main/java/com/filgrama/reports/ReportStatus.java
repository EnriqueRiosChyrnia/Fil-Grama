package com.filgrama.reports;

/** Estado del recurso reporte. v1 es síncrono: nace PENDING y termina COMPLETED o FAILED. */
public enum ReportStatus {
    PENDING,
    COMPLETED,
    FAILED
}
