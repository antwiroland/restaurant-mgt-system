package com.restaurantmanager.core.table;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.restaurantmanager.core.common.ApiException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class TableQrCodeService {
    public byte[] generatePng(String payload, int sizePx) {
        if (payload == null || payload.isBlank()) {
            throw new ApiException(400, "QR payload is required");
        }

        int normalizedSize = Math.max(120, Math.min(sizePx, 1024));

        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload.trim(), BarcodeFormat.QR_CODE, normalizedSize, normalizedSize);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new ApiException(500, "Could not generate QR code image");
        }
    }
}
