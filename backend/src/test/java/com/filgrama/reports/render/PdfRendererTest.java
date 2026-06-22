package com.filgrama.reports.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Render del PDF sin Spring ni DB: verifica que el XHTML tiene la grilla/agrupación esperada y que
 * openhtmltopdf produce un PDF no vacío (cabecera {@code %PDF}). Cubre el caso narrativa ausente.
 */
class PdfRendererTest {

    private final PdfRenderer renderer = new PdfRenderer();

    @Test
    void extendedHtmlHasGridGroupingStarAndDates() {
        String html = renderer.buildHtml(RenderFixtures.extended());

        assertThat(html).startsWith("<?xml");
        // Agrupación por tipo (como con como) y grilla de miniaturas.
        assertThat(html).contains("Reels").contains("Feed").contains("class=\"grid\"");
        assertThat(html).contains("class=\"thumb reel\"").contains("class=\"thumb feed\"");
        // Estrella en destacadas + métrica sobre la imagen + fecha debajo.
        assertThat(html).contains("★").contains("class=\"metric\"").contains("class=\"date\"");
        // Miniatura embebida en base64 (data-URI) y XML escapado (& -> &amp;).
        assertThat(html).contains("data:image/png;base64,");
        assertThat(html).contains("&amp;").doesNotContain("<con cariño>");
        // Sin narrativa → sin sección "Análisis del mes".
        assertThat(html).doesNotContain("Análisis del mes");
    }

    @Test
    void extendedRendersNonEmptyPdf() {
        byte[] pdf = renderer.render(RenderFixtures.extended());

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(1000);
        String head = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertThat(head).isEqualTo("%PDF-");
    }

    @Test
    void summaryRendersNonEmptyPdf() {
        byte[] pdf = renderer.render(RenderFixtures.summary());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void narrativeSectionAppearsOnlyWhenPresent() {
        String withNarrative = renderer.buildHtml(RenderFixtures.extendedWithNarrative());
        assertThat(withNarrative).contains("Análisis del mes").contains("creció un 7%");
    }
}
