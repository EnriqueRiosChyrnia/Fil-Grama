package com.filgrama.mcp.dto;

import java.time.LocalDate;
import java.util.List;

import com.filgrama.reports.data.ReportData;

/** Salida de {@code get_audience_demographics}: demografía de seguidores por red del período. */
public record DemographicsView(
        ReportData.Client client,
        LocalDate from,
        LocalDate to,
        List<PlatformDemographics> byPlatform) {

    /** Demografía (ciudades/países/edades/géneros) de una red; solo IG/FB la exponen. */
    public record PlatformDemographics(String platform, ReportData.Demographics demographics) {
    }
}
