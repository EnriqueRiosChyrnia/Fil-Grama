package com.filgrama.reports.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.filgrama.error.ApiException;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.ContentTypeShare;
import com.filgrama.reports.data.ReportData.Demographics;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.PostGroup;
import com.filgrama.reports.data.ReportData.ProfileActivity;
import com.filgrama.reports.data.ReportData.ReachEvolution;
import com.filgrama.reports.data.ReportData.ReportPost;
import com.filgrama.reports.data.ReportData.Segment;
import com.filgrama.reports.data.ReportData.ViewsFollowerSplit;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Renderiza el {@link ReportData} a PDF: arma un <b>XHTML print-friendly</b> con la <b>marca
 * Fil-Grama</b> (paleta azul/gris de {@code design/filgrama-colors.css}, tipografía Public Sans — la
 * misma del front) y lo rinde con <b>openhtmltopdf</b>. El reporte manual de referencia
 * ({@code Reporte_Molinos_Don_Alexis_Junio_2026.*} en la raíz) es <b>sólo</b> guía de layout/estructura
 * (secciones, densidad, tipos de gráfico) — NO de paleta ni fuentes, que eran de ese cliente. Por
 * defecto todo reporte sale con la marca de la agencia; la personalización por cliente es spec futura
 * (spec/11), no implementada acá. Las miniaturas se embeben como {@code data:image/...;base64,...}
 * (las resuelve el {@link ThumbnailLoader}). Mismo {@code ReportData} que el Markdown. La sección
 * "Análisis del mes" sale sólo si hay narrativa.
 */
@Component
@Slf4j
public class PdfRenderer {

    private static final int CAPTION_MAX = 80;
    private static final int DEMOGRAPHICS_TOP_N = 6;
    private static final String FONT_FAMILY = "Public Sans";

    // ---- Paleta de marca Fil-Grama (design/filgrama-colors.css) ----
    private static final String BLUE_50 = "#EAF2FB";
    private static final String BLUE_100 = "#CADFF5";
    private static final String BLUE_300 = "#6BA6E2";
    private static final String BLUE_500 = "#1E66BC"; // color de marca
    private static final String BLUE_700 = "#0F3F78";
    private static final String GRAY_50 = "#F5F7FA";
    private static final String GRAY_100 = "#E9EDF2";
    private static final String GRAY_200 = "#D4DAE3";
    private static final String GRAY_400 = "#8A95A6";
    private static final String GRAY_500 = "#647084";
    private static final String GRAY_700 = "#353D4D";
    private static final String GRAY_900 = "#141921";
    private static final String WHITE = "#FFFFFF";

    public byte[] render(ReportData data) {
        String html = buildHtml(data);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.warn("Fallo al renderizar el PDF del reporte: {}", e.toString());
            throw ApiException.unprocessable("No se pudo generar el PDF del reporte");
        }
    }

    /** Registra los 4 pesos estáticos de Public Sans embebidos (misma fuente que el front, spec/07). */
    private void registerFonts(PdfRendererBuilder builder) {
        registerFont(builder, "PublicSans-Regular.ttf", 400);
        registerFont(builder, "PublicSans-Medium.ttf", 500);
        registerFont(builder, "PublicSans-SemiBold.ttf", 600);
        registerFont(builder, "PublicSans-Bold.ttf", 700);
    }

    private void registerFont(PdfRendererBuilder builder, String filename, int weight) {
        String resource = "/fonts/" + filename;
        if (PdfRenderer.class.getResource(resource) == null) {
            log.warn("Fuente {} no está en el classpath; se omite ({})", resource, FONT_FAMILY);
            return;
        }
        builder.useFont(() -> {
            InputStream in = PdfRenderer.class.getResourceAsStream(resource);
            if (in == null) {
                throw new IllegalStateException("No se pudo abrir la fuente " + resource);
            }
            return in;
        }, FONT_FAMILY, weight, FontStyle.NORMAL, true);
    }

    // ============================ HTML ============================

    /** Arma el XHTML del reporte. Package-visible para test del layout sin levantar el PDF. */
    String buildHtml(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>");
        sb.append("<meta charset=\"UTF-8\"/>");
        sb.append("<style>").append(css()).append("</style></head><body>");

        cover(sb, data);

        for (PlatformKpis pk : data.kpis()) {
            platformSummarySection(sb, pk);
            reachSection(sb, pk);
            interactionsSection(sb, pk);
            audienceSection(sb, pk);
        }

        sb.append("<div class=\"section\">");
        if (data.reportType() == ReportType.SUMMARY) {
            sb.append("<div class=\"phead\"><h2>Contenido destacado</h2></div>");
            sb.append("<p class=\"lead\">Top ").append(data.topPosts().size())
                    .append(" publicaciones del período.</p>");
            grid(sb, data.topPosts(), true);
        } else {
            extendedBody(sb, data);
        }
        sb.append("</div>");

        narrative(sb, data);
        sb.append("</body></html>");
        return sb.toString();
    }

    // ---------------------------- Portada ----------------------------

    private void cover(StringBuilder sb, ReportData data) {
        sb.append("<div class=\"cover\">");
        sb.append("<div class=\"brand\">FIL<span class=\"dot\">·</span>GRAMA</div>");
        sb.append("<div class=\"eyebrow\">Reporte de redes sociales · ")
                .append(data.reportType() == ReportType.SUMMARY ? "Resumen" : "Extendido").append("</div>");
        sb.append("<h1>").append(esc(data.client().name())).append("</h1>");
        sb.append("<div class=\"sub\">").append(esc(String.join(" · ", data.platforms()))).append("</div>");
        sb.append("<div class=\"meta\">");
        metaRow(sb, "Período", data.period().from() + " – " + data.period().to(),
                "Redes analizadas", data.platforms().isEmpty() ? "—" : String.join(", ", data.platforms()));
        metaRow(sb, "Tipo de reporte", data.reportType() == ReportType.SUMMARY ? "Resumen" : "Extendido",
                "Preparado por", "Fil-Grama");
        sb.append("</div></div>");
    }

    private void metaRow(StringBuilder sb, String label1, String val1, String label2, String val2) {
        sb.append("<div class=\"meta-row\">");
        sb.append("<div class=\"meta-item\"><div class=\"meta-label\">").append(esc(label1))
                .append("</div><div class=\"meta-val\">").append(esc(val1)).append("</div></div>");
        sb.append("<div class=\"meta-item\"><div class=\"meta-label\">").append(esc(label2))
                .append("</div><div class=\"meta-val\">").append(esc(val2)).append("</div></div>");
        sb.append("</div>");
    }

    // ---------------------------- Secciones por red ----------------------------

    private void platformSummarySection(StringBuilder sb, PlatformKpis pk) {
        openSection(sb, "Resumen del período", platformLabel(pk.platform()));
        sb.append("<p class=\"lead\">KPIs principales de ").append(esc(platformLabel(pk.platform())))
                .append(" en el período.</p>");
        kpiGrid(sb, pk);
        if (pk.viewsFollowerSplit() != null) {
            ViewsFollowerSplit split = pk.viewsFollowerSplit();
            barsCard(sb, "Origen de las visualizaciones", List.of(
                    new Bar("Seguidores", split.followerPct()),
                    new Bar("No seguidores", split.nonFollowerPct())));
        }
        closeSection(sb);
    }

    private void reachSection(StringBuilder sb, PlatformKpis pk) {
        List<Bar> contentTypeBars = pk.viewsByContentType().stream()
                .map(c -> new Bar(c.displayType(), c.pct()))
                .toList();
        List<StatRow> activityRows = profileActivityRows(pk.profileActivity());
        if (contentTypeBars.isEmpty() && activityRows.isEmpty()) {
            return;
        }
        openSection(sb, "Alcance y visualizaciones", platformLabel(pk.platform()));
        barsCard(sb, "Visualizaciones por tipo de contenido", contentTypeBars);
        statsCard(sb, "Actividad en el perfil", activityRows);
        closeSection(sb);
    }

    private void interactionsSection(StringBuilder sb, PlatformKpis pk) {
        List<StatRow> rows = pk.interactionsByAction().stream()
                .map(k -> new StatRow(k.displayName(), ReportFormatting.count(k.value())))
                .toList();
        if (rows.isEmpty()) {
            return;
        }
        openSection(sb, "Interacciones por acción", platformLabel(pk.platform()));
        statsCard(sb, "Qué tipo de interacción generó el contenido", rows);
        closeSection(sb);
    }

    private void audienceSection(StringBuilder sb, PlatformKpis pk) {
        Demographics d = pk.demographics();
        if (d == null) {
            return;
        }
        openSection(sb, "Tu público", platformLabel(pk.platform()));
        barsCard(sb, "Principales ciudades", topSegments(d.cities()));
        barsCard(sb, "Principales países", topSegments(d.countries()));
        barsCard(sb, "Rangos de edad", topSegments(d.ageRanges()));
        barsCard(sb, "Sexo", topSegments(d.genders()));
        closeSection(sb);
    }

    private static List<Bar> topSegments(List<Segment> segments) {
        if (segments == null) {
            return List.of();
        }
        return segments.stream().limit(DEMOGRAPHICS_TOP_N).map(s -> new Bar(s.label(), s.pct())).toList();
    }

    private static List<StatRow> profileActivityRows(ProfileActivity activity) {
        if (activity == null) {
            return List.of();
        }
        List<StatRow> rows = new ArrayList<>();
        if (activity.profileViews() != null) {
            rows.add(new StatRow("Visitas al perfil", ReportFormatting.count(activity.profileViews())));
        }
        if (activity.whatsappTaps() != null) {
            rows.add(new StatRow("Clics al enlace de WhatsApp", ReportFormatting.count(activity.whatsappTaps())));
        }
        if (activity.directionTaps() != null) {
            rows.add(new StatRow("Clics en la ubicación", ReportFormatting.count(activity.directionTaps())));
        }
        return rows;
    }

    private void openSection(StringBuilder sb, String title, String badge) {
        sb.append("<div class=\"section\"><div class=\"phead\"><h2>").append(esc(title)).append("</h2>");
        if (badge != null) {
            sb.append("<span class=\"badge\">").append(esc(badge)).append("</span>");
        }
        sb.append("</div>");
    }

    private void closeSection(StringBuilder sb) {
        sb.append("</div>");
    }

    private static String platformLabel(String platform) {
        return switch (platform) {
            case "INSTAGRAM" -> "Instagram";
            case "FACEBOOK" -> "Facebook";
            case "TIKTOK" -> "TikTok";
            default -> platform;
        };
    }

    // ---------------------------- Barras / stats (reemplazan los donuts: sin soporte SVG) ----------------------------

    private record Bar(String label, BigDecimal pct) {
    }

    private record StatRow(String label, String value) {
    }

    private void barsCard(StringBuilder sb, String title, List<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return;
        }
        sb.append("<div class=\"card\"><h3>").append(esc(title)).append("</h3><div class=\"bars\">");
        for (Bar b : bars) {
            String width = clampPct(b.pct());
            sb.append("<div class=\"bar-row\"><div class=\"bar-top\"><span>").append(esc(b.label()))
                    .append("</span><b>").append(esc(ReportFormatting.percent(b.pct()))).append("</b></div>");
            sb.append("<div class=\"bar-track\"><div class=\"bar-fill\" style=\"width:")
                    .append(width).append("%\"></div></div></div>");
        }
        sb.append("</div></div>");
    }

    private void statsCard(StringBuilder sb, String title, List<StatRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        sb.append("<div class=\"card\"><h3>").append(esc(title)).append("</h3>");
        for (StatRow r : rows) {
            sb.append("<div class=\"stat-row\"><span>").append(esc(r.label())).append("</span><span class=\"v\">")
                    .append(esc(r.value())).append("</span></div>");
        }
        sb.append("</div>");
    }

    /** Ancho de barra en % (0..100), acotado; {@code null}/negativo → 0. */
    private static String clampPct(BigDecimal pct) {
        if (pct == null) {
            return "0";
        }
        BigDecimal capped = pct.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return capped.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    // ---------------------------- KPIs ----------------------------

    private void kpiGrid(StringBuilder sb, PlatformKpis pk) {
        sb.append("<div class=\"kpi-grid\">");
        boolean any = false;
        if (pk.reach() != null && pk.reach().current() != null) {
            sb.append(kpiCard("Alcance", ReportFormatting.count(pk.reach().current()), reachDelta(pk.reach())));
            any = true;
        }
        if (pk.engagementRate() != null) {
            sb.append(kpiCard("Engagement", ReportFormatting.percent(pk.engagementRate()), null));
            any = true;
        }
        if (pk.followerGrowth() != null) {
            sb.append(kpiCard("Seguidores nuevos", ReportFormatting.signedCount(pk.followerGrowth()), null));
            any = true;
        }
        for (Kpi k : pk.metrics()) {
            sb.append(kpiCard(k.displayName(), ReportFormatting.count(k.value()),
                    k.delta() == null ? null : ReportFormatting.signedCount(k.delta())));
            any = true;
        }
        sb.append("</div>");
        if (!any) {
            sb.append("<p class=\"empty\">Aún no hay datos para este rango.</p>");
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

    // ---------------------------- Contenido (posts) ----------------------------

    private void extendedBody(StringBuilder sb, ReportData data) {
        sb.append("<div class=\"phead\"><h2>Contenido destacado</h2></div>");
        highlights(sb, "Destacadas del período", "Con más margen de mejora", data.postHighlights());
        sb.append("<h3 class=\"sub-title\">Publicaciones por red y tipo</h3>");
        groups(sb, data.postGroups());

        if (!data.storyGroups().isEmpty() || !data.storyHighlights().isEmpty()) {
            sb.append("<h3 class=\"sub-title\">Historias</h3>");
            highlights(sb, "Historias destacadas", "Historias con más margen", data.storyHighlights());
            groups(sb, data.storyGroups());
        }
    }

    private void highlights(StringBuilder sb, String topTitle, String bottomTitle, Highlights h) {
        if (h == null || h.isEmpty()) {
            return;
        }
        if (h.top() != null && !h.top().isEmpty()) {
            sb.append("<h3 class=\"hl\">").append(esc(topTitle)).append("</h3>");
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

    /** Grilla de miniaturas: métrica sobre la imagen, fecha (+ watch-time en reels) debajo, estrella si {@code starred}. */
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
                sb.append("<span class=\"star\">TOP</span>");
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
            sb.append("<div class=\"date\">").append(esc(p.publishedAtLocal()));
            if (p.watchTimeSeconds() != null) {
                sb.append(" · ").append(esc(ReportFormatting.count(p.watchTimeSeconds()))).append("s promedio");
            }
            sb.append("</div>");
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
        sb.append("<div class=\"section\"><div class=\"phead\"><h2>Análisis del mes</h2></div>");
        sb.append("<div class=\"narrative\">").append(esc(data.narrativeMd())).append("</div></div>");
    }

    // ============================ CSS (sin '>' ni '&' para no romper el XHTML) ============================

    private static String css() {
        String template = """
                @page { size: A4; margin: 1.6cm; }
                body { font-family: '{{font}}', sans-serif; color: {{gray900}}; font-size: 10.5px; line-height: 1.5; }
                h1, h2, h3, h4 { font-family: '{{font}}', sans-serif; font-weight: 700; }
                .section { page-break-before: always; }

                /* ---------- Portada ---------- */
                .cover { padding: 8mm 0 0 0; }
                .cover .brand { font-size: 15px; font-weight: 700; letter-spacing: .12em; color: {{blue}}; }
                .cover .brand .dot { color: {{blue}}; }
                .cover .eyebrow { font-family: '{{font}}', sans-serif; letter-spacing: .18em; text-transform: uppercase;
                     font-size: 9px; color: {{gray500}}; font-weight: 700; margin-top: 30mm; }
                .cover h1 { font-size: 30px; line-height: 1.15; font-weight: 800; margin-top: 4mm; color: {{gray900}}; }
                .cover .sub { font-size: 12px; margin-top: 4mm; color: {{gray500}}; font-weight: 400; }
                .cover .meta { margin-top: 30mm; border-top: 2px solid {{blue}}; padding-top: 6mm; }
                .cover .meta-row { margin-bottom: 5mm; }
                .cover .meta-item { display: inline-block; vertical-align: top; width: 48%; }
                .cover .meta-label { font-family: '{{font}}', sans-serif; font-size: 8px; letter-spacing: .12em;
                     text-transform: uppercase; color: {{gray500}}; }
                .cover .meta-val { font-size: 11px; color: {{gray900}}; margin-top: 1mm; font-weight: 600; }

                /* ---------- Encabezados de sección ---------- */
                .phead { border-bottom: 2px solid {{blue}}; padding-bottom: 3mm; margin-bottom: 6mm; }
                .phead h2 { font-size: 16px; font-weight: 800; color: {{gray900}}; display: inline; }
                .phead .badge { float: right; font-family: '{{font}}', sans-serif; font-weight: 700; font-size: 9px;
                     letter-spacing: .06em; text-transform: uppercase; color: {{blue700}}; background: {{blue50}};
                     border-radius: 2mm; padding: 1mm 3mm; }
                .lead { color: {{gray500}}; font-size: 10px; margin-bottom: 5mm; }
                .empty { color: {{gray400}}; font-style: italic; }
                .count { color: {{gray400}}; font-weight: normal; }
                .sub-title { margin-top: 8mm; }

                /* ---------- KPIs ---------- */
                .kpi-grid { margin-bottom: 4mm; }
                .kpi { display: inline-block; vertical-align: top; width: 23%; margin: 0 1% 8px 0;
                       padding: 5mm 4mm; background: {{gray50}}; border: 1px solid {{gray200}};
                       border-top: 3px solid {{blue}}; border-radius: 2mm; box-sizing: border-box; }
                .kpi-val { font-size: 15px; font-weight: 800; color: {{gray900}}; }
                .kpi-label { font-size: 8px; color: {{gray500}}; margin-top: 1mm; }
                .kpi-delta { font-size: 8px; color: {{gray500}}; font-weight: 700; margin-top: 1mm; }

                /* ---------- Cards / barras / stats ---------- */
                .card { background: {{white}}; border: 1px solid {{gray200}}; border-radius: 2mm; padding: 5mm;
                        margin-bottom: 5mm; }
                .card h3 { font-size: 11px; color: {{gray900}}; margin-bottom: 3mm; font-weight: 700; }
                .bar-row { margin-bottom: 3mm; }
                .bar-top { font-size: 9px; margin-bottom: 1mm; color: {{gray700}}; }
                .bar-top b { float: right; color: {{gray900}}; }
                .bar-track { height: 3mm; background: {{gray100}}; border-radius: 1.5mm; overflow: hidden; clear: both; }
                .bar-fill { height: 100%; border-radius: 1.5mm; background: {{blue}}; }
                .stat-row { padding: 2mm 0; border-bottom: 1px solid {{gray100}}; font-size: 9.5px; }
                .stat-row:last-child { border-bottom: none; }
                .stat-row .v { float: right; font-weight: 800; color: {{gray900}}; }

                /* ---------- Grilla de contenido ---------- */
                .hl { color: {{blue}}; }
                .grid { }
                .grid .card { display: inline-block; vertical-align: top; width: 31%; margin: 0 1% 10px 0;
                       background: none; border: none; padding: 0; }
                .thumb { position: relative; background: {{gray100}}; border: 1px solid {{gray200}}; border-radius: 2mm;
                         overflow: hidden; text-align: center; }
                .thumb.feed { width: 100%; height: 150px; }
                .thumb.reel { width: 100%; height: 220px; }
                .thumb img { width: 100%; height: 100%; }
                .star { position: absolute; top: 4px; right: 4px; background: {{blue}}; color: {{white}};
                        font-family: '{{font}}', sans-serif; font-weight: 700; font-size: 7px;
                        letter-spacing: .04em; padding: 1mm 2mm; border-radius: 1.5mm; }
                .noimg { color: {{gray400}}; font-size: 9px; display: block; padding-top: 40px; }
                .metric { position: absolute; left: 0; bottom: 0; right: 0; background: {{gray700}};
                          color: {{white}}; font-size: 10px; font-weight: bold; padding: 2px 4px; }
                .metric small { font-weight: normal; font-size: 8px; }
                .date { font-size: 9px; color: {{gray500}}; margin-top: 2px; }
                .cap { font-size: 9px; color: {{gray500}}; }
                .narrative { font-size: 10.5px; line-height: 1.5; color: {{gray700}}; }
                """;
        return template
                .replace("{{font}}", FONT_FAMILY)
                .replace("{{blue700}}", BLUE_700)
                .replace("{{blue50}}", BLUE_50)
                .replace("{{blue}}", BLUE_500)
                .replace("{{gray900}}", GRAY_900)
                .replace("{{gray700}}", GRAY_700)
                .replace("{{gray500}}", GRAY_500)
                .replace("{{gray400}}", GRAY_400)
                .replace("{{gray200}}", GRAY_200)
                .replace("{{gray100}}", GRAY_100)
                .replace("{{gray50}}", GRAY_50)
                .replace("{{white}}", WHITE);
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
