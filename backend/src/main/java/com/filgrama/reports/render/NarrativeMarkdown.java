package com.filgrama.reports.render;

import java.util.List;

/**
 * Conversor mínimo Markdown → XHTML para la narrativa "Análisis del mes" en el PDF (spec/08). Sin
 * dependencia nueva (el track pide render simple): soporta párrafos, encabezados ({@code #}/{@code ##}/
 * {@code ###}), listas con viñetas ({@code -}/{@code *}) y énfasis en línea ({@code **negrita**},
 * {@code *cursiva*}). Todo el texto se escapa a XHTML bien formado antes de aplicar el formato, para no
 * romper el render de openhtmltopdf ni inyectar marcado arbitrario del modelo.
 */
final class NarrativeMarkdown {

    /** Convierte el markdown de la narrativa en un fragmento XHTML seguro (párrafos, listas, énfasis). */
    static String toXhtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder();
        List<String> paragraph = new java.util.ArrayList<>();
        boolean inList = false;

        for (String raw : lines) {
            String line = raw.strip();
            boolean isBullet = line.startsWith("- ") || line.startsWith("* ");
            boolean isHeading = line.startsWith("#");

            if (line.isEmpty() || isBullet || isHeading) {
                flushParagraph(out, paragraph);
            }
            if (!isBullet && inList) {
                out.append("</ul>");
                inList = false;
            }

            if (line.isEmpty()) {
                continue;
            }
            if (isHeading) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                out.append("<h4 class=\"nh\">").append(inline(line.substring(level).strip())).append("</h4>");
            } else if (isBullet) {
                if (!inList) {
                    out.append("<ul class=\"nlist\">");
                    inList = true;
                }
                out.append("<li>").append(inline(line.substring(2).strip())).append("</li>");
            } else {
                paragraph.add(line);
            }
        }
        flushParagraph(out, paragraph);
        if (inList) {
            out.append("</ul>");
        }
        return out.toString();
    }

    private static void flushParagraph(StringBuilder out, List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        out.append("<p>").append(inline(String.join(" ", paragraph))).append("</p>");
        paragraph.clear();
    }

    /** Escapa el texto y luego aplica énfasis en línea (negrita/cursiva). El orden importa: negrita 1º. */
    private static String inline(String raw) {
        String s = escape(raw);
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?<!\\*)\\*(?!\\*)([^*]+?)\\*(?!\\*)", "<i>$1</i>");
        s = s.replaceAll("(?<![\\p{Alnum}_])_([^_]+?)_(?![\\p{Alnum}_])", "<i>$1</i>");
        return s;
    }

    private static String escape(String text) {
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

    private NarrativeMarkdown() {
    }
}
