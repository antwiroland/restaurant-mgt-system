package com.restaurantmanager.core.table.qr;

import java.nio.charset.StandardCharsets;

public class TableQrPdfService {
    public byte[] exportTableQrPdf(String tableNumber) {
        String content = "%PDF-FAKE\nTABLE:" + tableNumber + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
