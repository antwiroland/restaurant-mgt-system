package com.restaurantmanager.core.reporting;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal multi-page PDF report writer: title, subtitle lines, summary key/values and a table with a repeated
 * header row, using the standard Helvetica fonts (no external dependency). Text outside Latin-1 is replaced.
 */
public final class PdfTableDocument {
    private static final double MARGIN = 36;
    private static final double ROW_HEIGHT = 14;

    private final double pageWidth;
    private final double pageHeight;
    private final String title;
    private final List<String> subtitle = new ArrayList<>();
    private final List<String[]> summary = new ArrayList<>();
    private String[] header = new String[0];
    private double[] widths = new double[0];
    private final List<String[]> rows = new ArrayList<>();

    public PdfTableDocument(String title, boolean landscape) {
        this.title = title;
        this.pageWidth = landscape ? 842 : 595;
        this.pageHeight = landscape ? 595 : 842;
    }

    public PdfTableDocument subtitle(String line) {
        subtitle.add(line);
        return this;
    }

    public PdfTableDocument summary(String label, String value) {
        summary.add(new String[]{label, value});
        return this;
    }

    /** @param relativeWidths column widths as proportions of the printable width */
    public PdfTableDocument columns(String[] header, double[] relativeWidths) {
        this.header = header;
        double total = 0;
        for (double w : relativeWidths) {
            total += w;
        }
        this.widths = new double[relativeWidths.length];
        for (int i = 0; i < relativeWidths.length; i++) {
            widths[i] = (pageWidth - 2 * MARGIN) * relativeWidths[i] / total;
        }
        return this;
    }

    public PdfTableDocument row(List<?> cells) {
        String[] values = new String[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            values[i] = cells.get(i) == null ? "" : cells.get(i).toString();
        }
        rows.add(values);
        return this;
    }

    public byte[] toBytes() {
        List<StringBuilder> pages = new ArrayList<>();
        StringBuilder page = newPage(pages);
        double y = pageHeight - MARGIN;

        text(page, "F2", 16, MARGIN, y - 16, title);
        y -= 28;
        for (String line : subtitle) {
            text(page, "F1", 9, MARGIN, y - 9, line);
            y -= 13;
        }
        if (!summary.isEmpty()) {
            y -= 6;
            for (String[] kv : summary) {
                text(page, "F2", 9, MARGIN, y - 9, kv[0]);
                text(page, "F1", 9, MARGIN + 160, y - 9, kv[1]);
                y -= 13;
            }
        }
        y -= 10;
        if (header.length > 0) {
            y = headerRow(page, y);
            for (String[] row : rows) {
                if (y - ROW_HEIGHT < MARGIN + 20) {
                    page = newPage(pages);
                    y = headerRow(page, pageHeight - MARGIN);
                }
                double x = MARGIN;
                for (int c = 0; c < header.length; c++) {
                    String value = c < row.length ? row[c] : "";
                    text(page, "F1", 8, x + 2, y - 10, fit(value, widths[c], 8));
                    x += widths[c];
                }
                page.append(String.format("0.85 G %.1f %.1f m %.1f %.1f l S 0 G\n", MARGIN, y - ROW_HEIGHT, pageWidth - MARGIN, y - ROW_HEIGHT));
                y -= ROW_HEIGHT;
            }
            if (rows.isEmpty()) {
                text(page, "F1", 9, MARGIN, y - 12, "No records for this period.");
            }
        }
        for (int i = 0; i < pages.size(); i++) {
            text(pages.get(i), "F1", 7, MARGIN, 20, "Page " + (i + 1) + " of " + pages.size());
        }
        return assemble(pages);
    }

    private double headerRow(StringBuilder page, double y) {
        page.append(String.format("0.93 g %.1f %.1f %.1f %.1f re f 0 g\n", MARGIN, y - ROW_HEIGHT, pageWidth - 2 * MARGIN, ROW_HEIGHT));
        double x = MARGIN;
        for (int c = 0; c < header.length; c++) {
            text(page, "F2", 8, x + 2, y - 10, fit(header[c], widths[c], 8));
            x += widths[c];
        }
        return y - ROW_HEIGHT;
    }

    private StringBuilder newPage(List<StringBuilder> pages) {
        StringBuilder page = new StringBuilder();
        pages.add(page);
        return page;
    }

    private static void text(StringBuilder page, String font, double size, double x, double y, String value) {
        page.append(String.format("BT /%s %.1f Tf %.1f %.1f Td (%s) Tj ET\n", font, size, x, y, escape(value)));
    }

    /** Truncates text to the column width using an average Helvetica glyph width of 0.52 em. */
    static String fit(String value, double width, double fontSize) {
        int maxChars = (int) Math.floor((width - 4) / (fontSize * 0.52));
        if (value.length() <= maxChars) {
            return value;
        }
        return maxChars <= 1 ? "" : value.substring(0, maxChars - 1) + "~";
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c < 32 || c > 255 ? '?' : c);
        }
        return sb.toString();
    }

    private byte[] assemble(List<StringBuilder> pages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        int fontRegular = 3;
        int fontBold = 4;
        int firstPage = 5;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(firstPage + i * 2).append(" 0 R ");
        }
        object(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        object(out, offsets, "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
        object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < pages.size(); i++) {
            int contentObject = firstPage + i * 2 + 1;
            object(out, offsets, String.format("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f] /Resources << /Font << /F1 %d 0 R /F2 %d 0 R >> >> /Contents %d 0 R >>",
                    pageWidth, pageHeight, fontRegular, fontBold, contentObject));
            byte[] content = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            offsets.add(out.size());
            write(out, offsets.size() + " 0 obj\n<< /Length " + content.length + " >>\nstream\n");
            out.writeBytes(content);
            write(out, "\nendstream\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, String.format("%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void object(ByteArrayOutputStream out, List<Integer> offsets, String body) {
        offsets.add(out.size());
        write(out, offsets.size() + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
