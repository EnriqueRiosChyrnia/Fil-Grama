package com.filgrama.reports.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Render Markdown sin Spring ni DB: SUMMARY/EXTENDED y la regla de la sección "Análisis del mes". */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void summaryHasHeaderKpisAndTopPosts() {
        String md = renderer.render(RenderFixtures.summary());

        assertThat(md).contains("# Reporte — Molinos del Sur");
        assertThat(md).contains("**Período:** 2026-05-01 a 2026-05-31");
        assertThat(md).contains("**Redes:** INSTAGRAM");
        assertThat(md).contains("## KPIs por red").contains("### INSTAGRAM").contains("Alcance");
        assertThat(md).contains("Evolución del alcance");
        assertThat(md).contains("publicaciones").contains("[ver](https://instagram.com/p/101)");
        // Sin narrativa → sin sección.
        assertThat(md).doesNotContain("Análisis del mes");
    }

    @Test
    void extendedGroupsByTypeAndHasHighlights() {
        String md = renderer.render(RenderFixtures.extended());

        assertThat(md).contains("# Reporte extendido — Molinos del Sur");
        assertThat(md).contains("Destacadas del mes").contains("Con más margen de mejora");
        assertThat(md).contains("## Publicaciones por red y tipo");
        assertThat(md).contains("### INSTAGRAM · Reels").contains("### INSTAGRAM · Feed");
    }

    @Test
    void narrativeSectionRendersWhenPresent() {
        String md = renderer.render(RenderFixtures.extendedWithNarrative());

        assertThat(md).contains("## Análisis del mes").contains("El alcance creció un 7%");
    }
}
