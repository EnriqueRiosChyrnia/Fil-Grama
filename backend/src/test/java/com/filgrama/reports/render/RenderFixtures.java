package com.filgrama.reports.render;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.Client;
import com.filgrama.reports.data.ReportData.ContentTypeShare;
import com.filgrama.reports.data.ReportData.Demographics;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.Period;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.PostGroup;
import com.filgrama.reports.data.ReportData.ProfileActivity;
import com.filgrama.reports.data.ReportData.ReachEvolution;
import com.filgrama.reports.data.ReportData.ReportPost;
import com.filgrama.reports.data.ReportData.Segment;
import com.filgrama.reports.data.ReportData.ViewsFollowerSplit;

/** Construye {@link ReportData} de ejemplo para los tests de renderizado (sin DB ni Spring). */
final class RenderFixtures {

    /** PNG 1x1 transparente en base64 (miniatura cacheada de prueba). */
    static final String PNG_DATA_URI = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private RenderFixtures() {
    }

    static Client client() {
        return new Client(7L, "Molinos del Sur & Cía", "America/Asuncion", "Plan Premium");
    }

    static Period period() {
        return new Period(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"),
                LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-30"));
    }

    static List<PlatformKpis> kpis() {
        Kpi reach = new Kpi("ig_reach", "Alcance", "count", new BigDecimal("125400"), new BigDecimal("8200"));
        Kpi inter = new Kpi("ig_total_interactions", "Interacciones", "count",
                new BigDecimal("9300"), new BigDecimal("-450"));
        ReachEvolution evo = new ReachEvolution(new BigDecimal("125400"), new BigDecimal("117200"),
                new BigDecimal("7.0"));
        Demographics demographics = new Demographics(
                List.of(new Segment("Encarnación", new BigDecimal("214"), new BigDecimal("0.27")),
                        new Segment("Asunción", new BigDecimal("120"), new BigDecimal("0.15"))),
                List.of(new Segment("Paraguay", new BigDecimal("620"), new BigDecimal("0.79")),
                        new Segment("Argentina", new BigDecimal("90"), new BigDecimal("0.11"))),
                List.of(new Segment("25-34", new BigDecimal("360"), new BigDecimal("0.46")),
                        new Segment("18-24", new BigDecimal("218"), new BigDecimal("0.28"))),
                List.of(new Segment("Mujeres", new BigDecimal("530"), new BigDecimal("0.68")),
                        new Segment("Hombres", new BigDecimal("252"), new BigDecimal("0.32"))));
        ViewsFollowerSplit split = new ViewsFollowerSplit(
                new BigDecimal("3616"), new BigDecimal("1484"), new BigDecimal("0.709"), new BigDecimal("0.291"));
        List<Kpi> interactions = List.of(
                new Kpi("ig_post_likes", "Me gusta", "count", new BigDecimal("72"), null),
                new Kpi("ig_post_comments", "Comentarios", "count", new BigDecimal("290"), null),
                new Kpi("ig_post_shares", "Compartidos", "count", new BigDecimal("22"), null),
                new Kpi("ig_post_saved", "Guardados", "count", new BigDecimal("1"), null),
                new Kpi("ig_post_reposts", "Reposts", "count", new BigDecimal("12"), null));
        List<ContentTypeShare> contentTypes = List.of(
                new ContentTypeShare("Reels", new BigDecimal("2958"), new BigDecimal("0.58")),
                new ContentTypeShare("Stories", new BigDecimal("1535"), new BigDecimal("0.301")),
                new ContentTypeShare("Feed", new BigDecimal("607"), new BigDecimal("0.119")));
        ProfileActivity activity = new ProfileActivity(
                new BigDecimal("363"), new BigDecimal("8"), new BigDecimal("2"));
        return List.of(new PlatformKpis("INSTAGRAM", List.of(reach, inter),
                new BigDecimal("0.074"), new BigDecimal("310"), evo,
                demographics, split, interactions, contentTypes, activity));
    }

    static ReportPost reel(long id, String when, String value, boolean cached) {
        return new ReportPost(id, "INSTAGRAM", "REEL", "Reels",
                Instant.parse(when), localFor(when), "https://instagram.com/p/" + id,
                "Receta de pan & molienda artesanal <con cariño>",
                cached ? PNG_DATA_URI : null, null, false,
                "ig_post_reach", "Alcance del post", new BigDecimal(value), new BigDecimal("13"));
    }

    static ReportPost feed(long id, String when, String value) {
        return new ReportPost(id, "INSTAGRAM", "IMAGE", "Feed",
                Instant.parse(when), localFor(when), "https://instagram.com/p/" + id,
                "Foto del molino", PNG_DATA_URI, null, false,
                "ig_post_reach", "Alcance del post", new BigDecimal(value), null);
    }

    static ReportData summary() {
        List<ReportPost> top = List.of(
                reel(101, "2026-05-20T13:00:00Z", "42000", true),
                feed(102, "2026-05-18T15:00:00Z", "31000"),
                reel(103, "2026-05-10T12:00:00Z", "21000", false));
        return new ReportData(ReportType.SUMMARY, ReportFormat.PDF, client(), period(),
                List.of("INSTAGRAM"), "reach", kpis(), top,
                List.of(), List.of(), emptyHighlights(), emptyHighlights(), null);
    }

    static ReportData extended() {
        ReportPost r1 = reel(101, "2026-05-20T13:00:00Z", "42000", true);
        ReportPost r2 = reel(103, "2026-05-10T12:00:00Z", "21000", false);
        ReportPost f1 = feed(102, "2026-05-18T15:00:00Z", "31000");
        PostGroup reels = new PostGroup("INSTAGRAM", "Reels", List.of(r1, r2));
        PostGroup feed = new PostGroup("INSTAGRAM", "Feed", List.of(f1));
        Highlights highlights = new Highlights(List.of(r1, f1), List.of(r2));
        return new ReportData(ReportType.EXTENDED, ReportFormat.PDF, client(), period(),
                List.of("INSTAGRAM"), "reach", kpis(), List.of(r1, f1),
                List.of(reels, feed), List.of(), highlights, emptyHighlights(), null);
    }

    static ReportData extendedWithNarrative() {
        ReportData base = extended();
        return new ReportData(base.reportType(), base.format(), base.client(), base.period(),
                base.platforms(), base.rankBy(), base.kpis(), base.topPosts(),
                base.postGroups(), base.storyGroups(), base.postHighlights(), base.storyHighlights(),
                "El alcance creció un 7% respecto al mes anterior. ¡Buen trabajo!");
    }

    static Highlights emptyHighlights() {
        return new Highlights(List.of(), List.of());
    }

    private static String localFor(String iso) {
        return Instant.parse(iso).atZone(java.time.ZoneId.of("America/Asuncion"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy",
                        java.util.Locale.forLanguageTag("es")));
    }
}
