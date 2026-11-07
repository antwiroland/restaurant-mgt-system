package com.restaurantmanager.core.table.qr;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/qr")
public class TableQrSessionController {
    private final TableSessionService sessionService;
    private final RestaurantTableRepository tableRepository;
    private final TableQrPdfService pdfService = new TableQrPdfService();
    private final String publicWebUrl;
    private final String venueName;

    public TableQrSessionController(TableSessionService sessionService,
                                    RestaurantTableRepository tableRepository,
                                    @Value("${app.public-web-url:http://localhost:3000}") String publicWebUrl,
                                    @Value("${app.venue-name:Restaurant}") String venueName) {
        this.sessionService = sessionService;
        this.tableRepository = tableRepository;
        this.publicWebUrl = publicWebUrl.replaceAll("/+$", "");
        this.venueName = venueName;
    }

    /** Public: guests scanning a table QR (web or app) link their session to the table. */
    @PostMapping("/sessions/link")
    public TableSessionService.TableSessionView linkSession(@Valid @RequestBody QrSessionRequest request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return sessionService.link(request.tableToken(), request.tableNumber(), request.sessionId(), principal);
    }

    @GetMapping("/sessions/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public java.util.List<TableSessionService.TableSessionView> activeSessions() {
        return sessionService.allActive();
    }

    @GetMapping("/sessions/{tableNumber}/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public TableSessionService.TableSessionView hasActiveSession(@PathVariable String tableNumber) {
        return sessionService.activeForTableNumber(tableNumber);
    }

    @DeleteMapping("/sessions/{tableNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public void closeSession(@PathVariable String tableNumber) {
        sessionService.closeForTableNumber(tableNumber);
    }

    /** Printable A6 table card with the table's scan QR code. */
    @GetMapping(value = "/tables/{tableNumber}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<byte[]> exportQrPdf(@PathVariable String tableNumber) {
        RestaurantTableEntity table = tableRepository.findByNumber(tableNumber)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        byte[] pdf = pdfService.exportTableQrPdf(table.getNumber(), publicWebUrl + "/scan/" + table.getQrToken(), venueName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("table-" + table.getNumber() + "-qr.pdf").build().toString())
                .body(pdf);
    }

    public record QrSessionRequest(@Size(max = 120) String tableToken,
                                   @Size(max = 20) String tableNumber,
                                   @NotBlank @Size(max = 120) String sessionId) {}
}
