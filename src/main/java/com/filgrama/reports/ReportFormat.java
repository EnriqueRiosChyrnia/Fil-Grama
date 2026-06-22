package com.filgrama.reports;

/** Formato de salida del reporte. */
public enum ReportFormat {
    MARKDOWN("text/markdown", "md"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    ReportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
