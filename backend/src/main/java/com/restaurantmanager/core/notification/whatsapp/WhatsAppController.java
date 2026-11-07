package com.restaurantmanager.core.notification.whatsapp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/whatsapp")
public class WhatsAppController {
    private final WhatsAppService whatsAppService;

    public WhatsAppController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    @PostMapping("/order-confirmation")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public WhatsAppSendResponse sendOrderConfirmation(@Valid @RequestBody WhatsAppOrderRequest request) {
        return new WhatsAppSendResponse(whatsAppService.sendOrderConfirmation(request.phone(), request.orderId()));
    }

    @PostMapping("/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public WhatsAppSendResponse sendReceipt(@Valid @RequestBody WhatsAppReceiptRequest request) {
        return new WhatsAppSendResponse(whatsAppService.sendReceipt(request.phone(), request.receiptLink(), request.reorderLink()));
    }

    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public WhatsAppSendResponse sendStatusUpdate(@Valid @RequestBody WhatsAppStatusRequest request) {
        return new WhatsAppSendResponse(whatsAppService.sendStatusUpdate(request.phone(), request.status()));
    }

    public record WhatsAppOrderRequest(@NotBlank String phone, @NotBlank String orderId) {}

    public record WhatsAppReceiptRequest(@NotBlank String phone, @NotBlank String receiptLink, @NotBlank String reorderLink) {}

    public record WhatsAppStatusRequest(@NotBlank String phone, @NotBlank String status) {}

    public record WhatsAppSendResponse(boolean sent) {}
}
