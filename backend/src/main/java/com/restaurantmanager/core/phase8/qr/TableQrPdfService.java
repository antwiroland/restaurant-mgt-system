package com.restaurantmanager.core.phase8.qr;

import java.nio.charset.StandardCharsets;

public class TableQrPdfService {
    public byte[] exportTableQrPdf(String tableNumber) {
        String content = "%PDF-FAKE\nTABLE:" + tableNumber + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
