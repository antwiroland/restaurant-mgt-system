package com.restaurantmanager.core.table.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Renders a printable A6 table card PDF: title, table number, QR code and the scan URL.
 * The PDF is written directly (QR as a Flate-compressed 8-bit grayscale image, text in Helvetica)
 * so no PDF library is required.
 */
public class TableQrPdfService {
    private static final int PAGE_WIDTH = 298;   // A6 portrait in points
    private static final int PAGE_HEIGHT = 420;
    private static final int QR_SIZE = 220;

    public byte[] exportTableQrPdf(String tableNumber) {
        return exportTableQrPdf(tableNumber, "TABLE:" + tableNumber, "Restaurant");
    }

    public byte[] exportTableQrPdf(String tableNumber, String scanUrl, String venueName) {
        BitMatrix matrix = encode(scanUrl);
        int modules = matrix.getWidth();
        byte[] pixels = new byte[modules * modules];
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                pixels[y * modules + x] = matrix.get(x, y) ? 0 : (byte) 0xFF;
            }
        }
        byte[] imageData = deflate(pixels);

        int qrX = (PAGE_WIDTH - QR_SIZE) / 2;
        int qrY = 110;
        String content = String.join("\n",
                "BT /F1 16 Tf " + centeredX(venueName, 16) + " 372 Td (" + escape(venueName) + ") Tj ET",
                "BT /F1 30 Tf " + centeredX("Table " + tableNumber, 30) + " 336 Td (" + escape("Table " + tableNumber) + ") Tj ET",
                "q " + QR_SIZE + " 0 0 " + QR_SIZE + " " + qrX + " " + qrY + " cm /Im1 Do Q",
                "BT /F1 12 Tf " + centeredX("Scan to view the menu and order", 12) + " 84 Td (Scan to view the menu and order) Tj ET",
                "BT /F1 7 Tf " + centeredX(scanUrl, 7) + " 60 Td (" + escape(scanUrl) + ") Tj ET",
                "");
        byte[] contentBytes = content.getBytes(StandardCharsets.ISO_8859_1);

        PdfWriter pdf = new PdfWriter();
        pdf.object("<< /Type /Catalog /Pages 2 0 R >>");
        pdf.object("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        pdf.object("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT + "] "
                + "/Resources << /Font << /F1 4 0 R >> /XObject << /Im1 5 0 R >> >> /Contents 6 0 R >>");
        pdf.object("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        pdf.stream("<< /Type /XObject /Subtype /Image /Width " + modules + " /Height " + modules
                + " /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode /Length " + imageData.length + " >>", imageData);
        pdf.stream("<< /Length " + contentBytes.length + " >>", contentBytes);
        pdf.object("<< /Title (" + escape("Table " + tableNumber + " QR") + ") /Producer (Restaurant Manager) >>");
        return pdf.finish(7);
    }

    private BitMatrix encode(String payload) {
        try {
            return new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException ex) {
            throw new IllegalStateException("Could not encode QR payload", ex);
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** Approximate centring using the average Helvetica glyph width (~0.5 em). */
    private static int centeredX(String text, int fontSize) {
        double width = text.length() * fontSize * 0.5;
        return (int) Math.max(12, (PAGE_WIDTH - width) / 2);
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

    private static final class PdfWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final List<Integer> offsets = new ArrayList<>();

        PdfWriter() {
            write("%PDF-1.4\n%âãÏÓ\n");
        }

        void object(String body) {
            begin();
            write(body + "\nendobj\n");
        }

        void stream(String dictionary, byte[] data) {
            begin();
            write(dictionary + "\nstream\n");
            out.writeBytes(data);
            write("\nendstream\nendobj\n");
        }

        byte[] finish(int infoObject) {
            int xref = out.size();
            write("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
            for (int offset : offsets) {
                write(String.format("%010d 00000 n \n", offset));
            }
            write("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R /Info " + infoObject + " 0 R >>\nstartxref\n"
                    + xref + "\n%%EOF\n");
            return out.toByteArray();
        }

        private void begin() {
            offsets.add(out.size());
            write(offsets.size() + " 0 obj\n");
        }

        private void write(String text) {
            out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}
