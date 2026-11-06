package com.restaurantmanager.core.reporting;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** RFC 4180 CSV with protection against spreadsheet formula injection. */
public final class CsvWriter {
    private final StringBuilder out = new StringBuilder("﻿"); // BOM so Excel detects UTF-8

    public CsvWriter row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    public byte[] toBytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        // Cells starting with these characters are executed as formulas by spreadsheet apps.
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
