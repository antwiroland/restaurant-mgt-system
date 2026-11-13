package com.restaurantmanager.gateway.hikvision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reads the parts of a never-ending {@code multipart/mixed} response, as sent by ISAPI's alert stream. Each part has
 * headers (Content-Type, usually Content-Length) and a body; picture parts (image/jpeg) are read and skipped.
 */
public class MultipartStreamReader {
    private static final int MAX_PART = 4 * 1024 * 1024;

    public record Part(String contentType, byte[] body) {
        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private final InputStream in;
    private final String boundary;

    public MultipartStreamReader(InputStream in, String boundary) {
        this.in = in;
        this.boundary = boundary.startsWith("--") ? boundary.substring(2) : boundary;
    }

    /** The boundary from a Content-Type header value such as {@code multipart/mixed; boundary=MIME_boundary}. */
    public static String boundaryOf(String contentType) {
        if (contentType == null) return "boundary";
        for (String piece : contentType.split(";")) {
            String p = piece.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                return p.substring(9).replace("\"", "").trim();
            }
        }
        return "boundary";
    }

    /** The next part, or null at the end of the stream. */
    public Part next() throws IOException {
        // Skip to the next boundary line.
        String line;
        do {
            line = readLine();
            if (line == null) return null;
        } while (!line.trim().startsWith("--" + boundary));
        if (line.trim().equals("--" + boundary + "--")) return null;

        String contentType = "";
        int length = -1;
        while ((line = readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (name.equals("content-type")) contentType = value;
            if (name.equals("content-length")) {
                try {
                    length = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    length = -1;
                }
            }
        }
        if (line == null) return null;
        if (length >= 0 && length <= MAX_PART) {
            return new Part(contentType, in.readNBytes(length));
        }
        // No length: the body runs until the next boundary line.
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while ((line = peekBodyLine()) != null) {
            if (line.trim().startsWith("--" + boundary)) {
                pending = line;
                break;
            }
            if (body.size() > 0) body.write('\n');
            body.write(line.getBytes(StandardCharsets.UTF_8));
            if (body.size() > MAX_PART) break;
        }
        return new Part(contentType, body.toByteArray());
    }

    private String pending;

    private String peekBodyLine() throws IOException {
        return readLine();
    }

    private String readLine() throws IOException {
        if (pending != null) {
            String p = pending;
            pending = null;
            return p;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > MAX_PART) break;
        }
        if (b == -1 && buf.size() == 0) return null;
        return buf.toString(StandardCharsets.UTF_8);
    }
}
