package com.filgrama.reports.render;

import java.util.List;

import org.springframework.stereotype.Component;

import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData;
import com.filgrama.reports.data.ReportData.Highlights;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.PostGroup;
import com.filgrama.reports.data.ReportData.ReachEvolution;
import com.filgrama.reports.data.ReportData.ReportPost;

/**
 * Renderiza el {@link ReportData} a Markdown (salida liviana). El MD no arma la grilla visual: usa
 * secciones por red/tipo con tablas y links a la miniatura/permalink. La sección "Análisis del mes"
 * sale sólo si hay narrativa (en v1, nunca). Mismo {@code ReportData} que el PDF.
 */
@Component
public class MarkdownRenderer {

    private static final int CAPTION_MAX = 60;

    public String render(ReportData data) {
        StringBuilder sb = new StringBuilder();
        header(sb, data);
        kpisByPlatform(sb, data.kpis());

        if (data.reportType() == ReportType.SUMMARY) {
            section(sb, "Top " + data.topPosts().size() + " publicaciones");
            postsTable(sb, data.topPosts());
        } else {
            extendedBody(sb, data);
        }

        narrative(sb, data);
        return sb.toString();
    }

    private void header(StringBuilder sb, ReportData data) {
        String title = data.reportType() == ReportType.SUMMARY ? "Reporte" : "Reporte extendido";
        sb.append("# ").append(title).append(" — ").append(safe(data.client().name())).append("\n\n");
        sb.append("**Período:** ").append(data.period().from()).append(" a ").append(data.period().to())
                .append("  \n");
        sb.append("**Redes:** ").append(String.join(", ", data.platforms())).append("  \n");
        sb.append("**Tipo:** ")
                .append(data.reportType() == ReportType.SUMMARY ? "Resumen" : "Extendido")
                .append("  \n");
        if (data.rankBy() != null) {
            sb.append("**Orden de destacadas:** ").append(data.rankBy()).append("  \n");
        }
        sb.append('\n');
    }

    private void kpisByPlatform(StringBuilder sb, List<PlatformKpis> kpis) {
        section(sb, "KPIs por red");
        if (kpis.isEmpty()) {
            sb.append("_Aún no hay datos para este rango._\n\n");
            return;
        }
        for (PlatformKpis pk : kpis) {
            sb.append("### ").append(pk.platform()).append("\n\n");
            if (pk.metrics().isEmpty()) {
                sb.append("_Sin datos en el período._\n\n");
            } else {
                sb.append("| Métrica | Valor | vs período anterior |\n|---|---:|---:|\n");
                for (Kpi k : pk.metrics()) {
                    sb.append("| ").append(safe(k.displayName()))
                            .append(" | ").append(ReportFormatting.count(k.value()))
                            .append(" | ").append(ReportFormatting.signedCount(k.delta()))
                            .append(" |\n");
                }
                sb.append('\n');
            }
            if (pk.engagementRate() != null) {
                sb.append("- **Engagement:** ").append(ReportFormatting.percent(pk.engagementRate())).append('\n');
            }
            if (pk.followerGrowth() != null) {
                sb.append("- **Seguidores nuevos:** ")
                        .append(ReportFormatting.signedCount(pk.followerGrowth())).append('\n');
            }
            reach(sb, pk.reach());
            sb.append('\n');
        }
    }

    private void reach(StringBuilder sb, ReachEvolution reach) {
        if (reach == null || reach.current() == null) {
            return;
        }
        sb.append("- **Evolución del alcance:** ").append(ReportFormatting.count(reach.current()));
        if (reach.deltaPct() != null) {
            sb.append(" (").append(ReportFormatting.signedPercentPoints(reach.deltaPct()))
                    .append(" vs período anterior)");
        }
        sb.append('\n');
    }

    private void extendedBody(StringBuilder sb, ReportData data) {
        highlights(sb, "Destacadas del mes", "Con más margen de mejora", data.postHighlights());
        section(sb, "Publicaciones por red y tipo");
        groups(sb, data.postGroups());

        if (!data.storyGroups().isEmpty() || !data.storyHighlights().isEmpty()) {
            section(sb, "Historias");
            highlights(sb, "Historias destacadas", "Historias con más margen", data.storyHighlights());
            groups(sb, data.storyGroups());
        }
    }

    private void highlights(StringBuilder sb, String topTitle, String bottomTitle, Highlights h) {
        if (h == null || h.isEmpty()) {
            return;
        }
        if (h.top() != null && !h.top().isEmpty()) {
            sb.append("### ⭐ ").append(topTitle).append("\n\n");
            postsTable(sb, h.top());
        }
        if (h.bottom() != null && !h.bottom().isEmpty()) {
            sb.append("### ").append(bottomTitle).append("\n\n");
            postsTable(sb, h.bottom());
        }
    }

    private void groups(StringBuilder sb, List<PostGroup> groups) {
        if (groups.isEmpty()) {
            sb.append("_Aún no hay publicaciones para este rango._\n\n");
            return;
        }
        for (PostGroup g : groups) {
            sb.append("### ").append(g.platform()).append(" · ").append(g.displayType())
                    .append(" (").append(g.posts().size()).append(")\n\n");
            postsTable(sb, g.posts());
        }
    }

    private void postsTable(StringBuilder sb, List<ReportPost> posts) {
        if (posts == null || posts.isEmpty()) {
            sb.append("_Sin publicaciones._\n\n");
            return;
        }
        sb.append("| Fecha | Red · Tipo | Métrica | Miniatura | Link |\n|---|---|---:|---|---|\n");
        for (ReportPost p : posts) {
            sb.append("| ").append(p.publishedAtLocal())
                    .append(" | ").append(p.platform()).append(" · ").append(p.displayType())
                    .append(" | ").append(metricCell(p))
                    .append(" | ").append(thumbCell(p))
                    .append(" | ").append(linkCell(p.permalink()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private static String metricCell(ReportPost p) {
        if (p.metricValue() == null) {
            return "—";
        }
        String name = p.metricName() == null ? "" : " " + p.metricName().toLowerCase();
        return ReportFormatting.count(p.metricValue()) + name;
    }

    private static String thumbCell(ReportPost p) {
        if (p.thumbnailUrl() != null) {
            return "[miniatura](" + p.thumbnailUrl() + ")";
        }
        return p.thumbnailDataUri() != null ? "cacheada" : "—";
    }

    private static String linkCell(String permalink) {
        return permalink == null || permalink.isBlank() ? "—" : "[ver](" + permalink + ")";
    }

    private void narrative(StringBuilder sb, ReportData data) {
        if (!data.hasNarrative()) {
            return;
        }
        section(sb, "Análisis del mes");
        sb.append(data.narrativeMd()).append("\n\n");
    }

    private static void section(StringBuilder sb, String title) {
        sb.append("## ").append(title).append("\n\n");
    }

    /** Escapa lo que rompería una celda de tabla Markdown (pipes y saltos de línea) y recorta. */
    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace("|", "\\|").replace("\n", " ").replace("\r", " ").trim();
        return clean.length() > CAPTION_MAX ? clean.substring(0, CAPTION_MAX - 1) + "…" : clean;
    }
}
