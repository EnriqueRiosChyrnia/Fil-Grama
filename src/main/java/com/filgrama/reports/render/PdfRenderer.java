package com.filgrama.reports.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Component;

import com.filgrama.error.ApiException;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.PostGroup;
import com.filgrama.reports.data.ReportData.ReachEvolution;
import com.filgrama.reports.data.ReportData.ReportPost;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Renderiza el {@link ReportData} a PDF: arma un <b>XHTML print-friendly</b> (grilla de miniaturas
 * 9:16 Reels / 1:1 Feed, métrica sobre la imagen, fecha debajo, estrella en destacadas) y lo rinde
 * con <b>openhtmltopdf</b>. Las miniaturas se embeben como {@code data:image/...;base64,...} (leídas
 * vía el {@code StoragePort} del track E por el {@link ThumbnailLoader}); en PDF no se hace fetch
 * remoto (sería una llamada de red en la generación) — sin cache va un placeholder. Mismo
 * {@code ReportData} que el Markdown. La sección "Análisis del mes" sale sólo si hay narrativa.
 */
@Component
@Slf4j
public class PdfRenderer {

    private static final int CAPTION_MAX = 80;
    /** Fuente opcional embebible; si está en el classpath se registra, si no se usa la default. */
    private static final String FONT_RESOURCE = "/fonts/report-font.ttf";
    private static final String FONT_FAMILY = "Filgrama Sans";

    public byte[] render(ReportData data) {
        String html = buildHtml(data);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFontIfPresent(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.warn("Fallo al renderizar el PDF del reporte: {}", e.toString());
            throw ApiException.unprocessable("No se pudo generar el PDF del reporte");
        }
    }

    private void registerFontIfPresent(PdfRendererBuilder builder) {
        if (PdfRenderer.class.getResource(FONT_RESOURCE) == null) {
            return;
        }
        builder.useFont(() -> {
            InputStream in = PdfRenderer.class.getResourceAsStream(FONT_RESOURCE);
            if (in == null) {
                throw new IllegalStateException("No se pudo abrir la fuente " + FONT_RESOURCE);
            }
            return in;
        }, FONT_FAMILY);
    }

    // ============================ HTML ============================

    /** Arma el XHTML del reporte. Package-visible para test del layout sin levantar el PDF. */
    String buildHtml(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>");
        sb.append("<meta charset=\"UTF-8\"/>");
        sb.append("<style>").append(css()).append("</style></head><body>");

        header(sb, data);
        kpis(sb, data.kpis());

        if (data.reportType() == ReportType.SUMMARY) {
            sb.append("<h2>Top ").append(data.topPosts().size()).append(" publicaciones</h2>");
            grid(sb, data.topPosts(), true);
        } else {
            extendedBody(sb, data);
        }

        narrative(sb, data);
        sb.append("</body></html>");
        return sb.toString();
    }

    private void header(StringBuilder sb, ReportData data) {
        String title = data.reportType() == ReportType.SUMMARY ? "Reporte" : "Reporte extendido";
        sb.append("<div class=\"head\">");
        sb.append("<h1>").append(esc(title)).append(" — ").append(esc(data.client().name())).append("</h1>");
        sb.append("<p class=\"meta\">");
        sb.append("Período <strong>").append(data.period().from()).append("</strong> a <strong>")
                .append(data.period().to()).append("</strong>");
        sb.append(" · Redes: ").append(esc(String.join(", ", data.platforms())));
        sb.append(" · ").append(data.reportType() == ReportType.SUMMARY ? "Resumen" : "Extendido");
        sb.append("</p></div>");
    }

    private void kpis(StringBuilder sb, List<PlatformKpis> kpis) {
        sb.append("<h2>KPIs por red</h2>");
        if (kpis.isEmpty()) {
            sb.append("<p class=\"empty\">Aún no hay datos para este rango.</p>");
            return;
        }
        for (PlatformKpis pk : kpis) {
            sb.append("<div class=\"platform\"><h3>").append(esc(pk.platform())).append("</h3>");
            sb.append("<div class=\"kpis\">");
            if (pk.reach() != null && pk.reach().current() != null) {
                sb.append(kpiCard("Alcance", ReportFormatting.count(pk.reach().current()),
                        reachDelta(pk.reach())));
            }
            if (pk.engagementRate() != null) {
                sb.append(kpiCard("Engagement", ReportFormatting.percent(pk.engagementRate()), null));
            }
            if (pk.followerGrowth() != null) {
                sb.append(kpiCard("Seguidores nuevos",
                        ReportFormatting.signedCount(pk.followerGrowth()), null));
            }
            for (Kpi k : pk.metrics()) {
                sb.append(kpiCard(k.displayName(), ReportFormatting.count(k.value()),
                        k.delta() == null ? null : ReportFormatting.signedCount(k.delta())));
            }
            sb.append("</div></div>");
        }
    }

    private static String reachDelta(ReachEvolution reach) {
        return reach.deltaPct() == null ? null : ReportFormatting.signedPercentPoints(reach.deltaPct());
    }

    private String kpiCard(String label, String value, String delta) {
        StringBuilder c = new StringBuilder("<div class=\"kpi\">");
        c.append("<div class=\"kpi-val\">").append(esc(value)).append("</div>");
        c.append("<div class=\"kpi-label\">").append(esc(label)).append("</div>");
        if (delta != null) {
            c.append("<div class=\"kpi-delta\">").append(esc(delta)).append("</div>");
        }
        c.append("</div>");
        return c.toString();
    }

    private void extendedBody(StringBuilder sb, ReportData data) {
        highlights(sb, "Destacadas del mes", "Con más margen de mejora", data.postHighlights());
        sb.append("<h2>Publicaciones por red y tipo</h2>");
        groups(sb, data.postGroups());

        if (!data.storyGroups().isEmpty() || !data.storyHighlights().isEmpty()) {
            sb.append("<h2>Historias</h2>");
            highlights(sb, "Historias destacadas", "Historias con más margen", data.storyHighlights());
            groups(sb, data.storyGroups());
        }
    }

    private void highlights(StringBuilder sb, String topTitle, String bottomTitle, Highlights h) {
        if (h == null || h.isEmpty()) {
            return;
        }
        if (h.top() != null && !h.top().isEmpty()) {
            sb.append("<h3 class=\"hl\">★ ").append(esc(topTitle)).append("</h3>");
            grid(sb, h.top(), true);
        }
        if (h.bottom() != null && !h.bottom().isEmpty()) {
            sb.append("<h3>").append(esc(bottomTitle)).append("</h3>");
            grid(sb, h.bottom(), false);
        }
    }

    private void groups(StringBuilder sb, List<PostGroup> groups) {
        if (groups.isEmpty()) {
            sb.append("<p class=\"empty\">Aún no hay publicaciones para este rango.</p>");
            return;
        }
        for (PostGroup g : groups) {
            sb.append("<h3>").append(esc(g.platform())).append(" · ").append(esc(g.displayType()))
                    .append(" <span class=\"count\">(").append(g.posts().size()).append(")</span></h3>");
            grid(sb, g.posts(), false);
        }
    }

    /** Grilla de miniaturas: métrica sobre la imagen, fecha debajo, estrella si {@code starred}. */
    private void grid(StringBuilder sb, List<ReportPost> posts, boolean starred) {
        if (posts == null || posts.isEmpty()) {
            sb.append("<p class=\"empty\">Sin publicaciones.</p>");
            return;
        }
        sb.append("<div class=\"grid\">");
        for (ReportPost p : posts) {
            boolean reel = "Reels".equals(p.displayType()) || "Stories".equals(p.displayType());
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"thumb ").append(reel ? "reel" : "feed").append("\">");
            if (starred) {
                sb.append("<span class=\"star\">★</span>");
            }
            if (p.thumbnailDataUri() != null) {
                sb.append("<img src=\"").append(p.thumbnailDataUri()).append("\" alt=\"\"/>");
            } else {
                sb.append("<span class=\"noimg\">sin miniatura</span>");
            }
            if (p.metricValue() != null) {
                sb.append("<span class=\"metric\">").append(esc(ReportFormatting.count(p.metricValue())));
                if (p.metricName() != null) {
                    sb.append(" <small>").append(esc(p.metricName())).append("</small>");
                }
                sb.append("</span>");
            }
            sb.append("</div>");
            sb.append("<div class=\"date\">").append(esc(p.publishedAtLocal())).append("</div>");
            if (p.caption() != null && !p.caption().isBlank()) {
                sb.append("<div class=\"cap\">").append(esc(trim(p.caption()))).append("</div>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");
    }

    private void narrative(StringBuilder sb, ReportData data) {
        if (!data.hasNarrative()) {
            return;
        }
        sb.append("<h2>Análisis del mes</h2><div class=\"narrative\">")
                .append(esc(data.narrativeMd())).append("</div>");
    }

    // ============================ CSS (sin '>' ni '&' para no romper el XHTML) ============================

    private static String css() {
        return """
                @page { size: A4; margin: 1.4cm; }
                body { font-family: 'Filgrama Sans', sans-serif; color: #1f2a37; font-size: 11px; }
                h1 { font-size: 20px; margin: 0 0 4px 0; color: #1d3557; }
                h2 { font-size: 15px; margin: 16px 0 8px 0; color: #1d3557;
                     border-bottom: 1px solid #dde3ea; padding-bottom: 3px; }
                h3 { font-size: 12px; margin: 10px 0 6px 0; color: #2a4d69; }
                h3.hl { color: #b8860b; }
                .meta { color: #5b6b7b; margin: 0 0 8px 0; }
                .count { color: #8a97a5; font-weight: normal; }
                .empty { color: #8a97a5; font-style: italic; }
                .kpis { }
                .kpi { display: inline-block; vertical-align: top; width: 23%; margin: 0 1% 8px 0;
                       padding: 6px 8px; background: #f4f7fb; border: 1px solid #e2e8f0; }
                .kpi-val { font-size: 16px; font-weight: bold; color: #1d3557; }
                .kpi-label { font-size: 9px; color: #5b6b7b; }
                .kpi-delta { font-size: 9px; color: #2f855a; }
                .grid { }
                .card { display: inline-block; vertical-align: top; width: 31%; margin: 0 1% 10px 0; }
                .thumb { position: relative; background: #e9eef4; border: 1px solid #d8e0ea;
                         overflow: hidden; text-align: center; }
                .thumb.feed { width: 100%; height: 150px; }
                .thumb.reel { width: 100%; height: 220px; }
                .thumb img { width: 100%; height: 100%; }
                .star { position: absolute; top: 3px; right: 5px; color: #f2c200; font-size: 16px; }
                .noimg { color: #9aa7b4; font-size: 9px; display: block; padding-top: 40px; }
                .metric { position: absolute; left: 0; bottom: 0; right: 0; background: #1d3557;
                          color: #ffffff; font-size: 10px; font-weight: bold; padding: 2px 4px; }
                .metric small { font-weight: normal; font-size: 8px; }
                .date { font-size: 9px; color: #5b6b7b; margin-top: 2px; }
                .cap { font-size: 9px; color: #6b7886; }
                .narrative { font-size: 11px; line-height: 1.4; }
                """;
    }

    // ============================ Escapes ============================

    private static String trim(String text) {
        String t = text.replace("\n", " ").replace("\r", " ").trim();
        return t.length() > CAPTION_MAX ? t.substring(0, CAPTION_MAX - 1) + "…" : t;
    }

    /** Escape XML para el contenido de texto y atributos (XHTML bien formado). */
    private static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
