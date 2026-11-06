package com.restaurantmanager.core.table.qr;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequestMapping("/qr")
public class TableQrSessionController {
    private final MobileSessionService mobileSessionService = new MobileSessionService();
    private final TableQrPdfService tableQrPdfService = new TableQrPdfService();

    @PostMapping("/sessions/link")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public void linkSession(@Valid @RequestBody QrSessionRequest request) {
        mobileSessionService.linkSession(request.tableNumber(), request.sessionId());
    }

    @GetMapping("/sessions/{tableNumber}/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public QrSessionActiveResponse hasActiveSession(@PathVariable String tableNumber) {
        return new QrSessionActiveResponse(mobileSessionService.hasActiveSession(tableNumber));
    }

    @DeleteMapping("/sessions/{tableNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public void closeSession(@PathVariable String tableNumber) {
        mobileSessionService.closeSession(tableNumber);
    }

    @GetMapping("/tables/{tableNumber}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<String> exportQrPdf(@PathVariable String tableNumber) {
        byte[] pdfBytes = tableQrPdfService.exportTableQrPdf(tableNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(Base64.getEncoder().encodeToString(pdfBytes));
    }

    public record QrSessionRequest(@NotBlank String tableNumber, @NotBlank String sessionId) {}

    public record QrSessionActiveResponse(boolean active) {}
}
