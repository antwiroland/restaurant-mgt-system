package com.restaurantmanager.core.phase8.runtime;

import com.restaurantmanager.core.phase8.common.DiscountType;
import com.restaurantmanager.core.phase8.promo.PromoCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/phase8")
public class Phase8Controller {
    private final Phase8RuntimeService runtimeService;

    public Phase8Controller(Phase8RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/promo/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PromoApplyResponse applyPromo(@Valid @RequestBody PromoApplyRequest request) {
        BigDecimal discount = runtimeService.applyPromo(
                request.code,
                request.discountType,
                request.discountValue,
                request.minOrderAmount,
                request.expiresAt,
                request.usageLimit,
                request.usedCount,
                request.active,
                request.subtotal,
                Instant.now()
        );
        return new PromoApplyResponse(discount, request.subtotal.subtract(discount));
    }

    @GetMapping("/promo-codes/validate/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PromoValidationResponse validatePromo(@PathVariable String code,
                                                 @RequestParam @NotNull BigDecimal subtotal) {
        PromoCode promo = runtimeService.validatePromo(code, subtotal, Instant.now());
        return new PromoValidationResponse(promo.getCode(), promo.getDiscountType(), promo.getDiscountValue(), true);
    }

    @PostMapping("/offer/free-items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public FreeItemsResponse freeItems(@Valid @RequestBody FreeItemsRequest request) {
        return new FreeItemsResponse(runtimeService.freeItemsFor(request.purchasedQuantity, request.buyQty, request.getQty));
    }

    @PostMapping("/loyalty/accrue")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public LoyaltyAccrueResponse accrue(@Valid @RequestBody LoyaltyAccrueRequest request) {
        int awarded = runtimeService.accrue(request.customerId, request.paymentAmount, request.orderId);
        return new LoyaltyAccrueResponse(awarded, runtimeService.balance(request.customerId));
    }

    @PostMapping("/loyalty/redeem")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public LoyaltyRedeemResponse redeem(@Valid @RequestBody LoyaltyRedeemRequest request) {
        var result = runtimeService.redeem(request.customerId, request.pointsToRedeem, request.orderTotal, request.orderId);
        return new LoyaltyRedeemResponse(result.newTotal(), result.remainingPoints(), result.redeemedPoints());
    }

    @GetMapping("/loyalty/{customerId}/balance")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public LoyaltyBalanceResponse balance(@PathVariable UUID customerId) {
        return new LoyaltyBalanceResponse(runtimeService.balance(customerId));
    }

    @GetMapping("/loyalty/{customerId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public List<?> history(@PathVariable UUID customerId) {
        return runtimeService.history(customerId);
    }

    @PostMapping("/qr/sessions/link")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public void linkSession(@Valid @RequestBody QrSessionRequest request) {
        runtimeService.linkSession(request.tableNumber, request.sessionId);
    }

    @GetMapping("/qr/sessions/{tableNumber}/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public QrSessionActiveResponse hasActiveSession(@PathVariable String tableNumber) {
        return new QrSessionActiveResponse(runtimeService.hasActiveSession(tableNumber));
    }

    @DeleteMapping("/qr/sessions/{tableNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public void closeSession(@PathVariable String tableNumber) {
        runtimeService.closeSession(tableNumber);
    }

    @GetMapping("/qr/tables/{tableNumber}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<String> exportQrPdf(@PathVariable String tableNumber) {
        byte[] pdfBytes = runtimeService.exportTableQrPdf(tableNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(Base64.getEncoder().encodeToString(pdfBytes));
    }

    @PostMapping("/whatsapp/order-confirmation")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public WhatsAppSendResponse sendOrderConfirmation(@Valid @RequestBody WhatsAppOrderRequest request) {
        return new WhatsAppSendResponse(runtimeService.sendOrderConfirmation(request.phone, request.orderId));
    }

    @PostMapping("/whatsapp/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public WhatsAppSendResponse sendReceipt(@Valid @RequestBody WhatsAppReceiptRequest request) {
        return new WhatsAppSendResponse(runtimeService.sendReceipt(request.phone, request.receiptLink, request.reorderLink));
    }

    @PostMapping("/whatsapp/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public WhatsAppSendResponse sendStatusUpdate(@Valid @RequestBody WhatsAppStatusRequest request) {
        return new WhatsAppSendResponse(runtimeService.sendStatusUpdate(request.phone, request.status));
    }

    public record PromoApplyRequest(
            @NotBlank String code,
            @NotNull DiscountType discountType,
            @NotNull BigDecimal discountValue,
            @NotNull BigDecimal minOrderAmount,
            @NotNull Instant expiresAt,
            @Min(1) int usageLimit,
            @Min(0) int usedCount,
            boolean active,
            @NotNull BigDecimal subtotal
    ) {}

    public record PromoApplyResponse(BigDecimal discount, BigDecimal newTotal) {}

    public record PromoValidationResponse(
            String code,
            DiscountType discountType,
            BigDecimal discountValue,
            boolean valid
    ) {}

    public record FreeItemsRequest(@Min(1) int purchasedQuantity, @Min(1) int buyQty, @Min(1) int getQty) {}

    public record FreeItemsResponse(int freeItems) {}

    public record LoyaltyAccrueRequest(
            @NotNull UUID customerId,
            @NotNull BigDecimal paymentAmount,
            @NotBlank String orderId
    ) {}

    public record LoyaltyAccrueResponse(int pointsAwarded, int currentBalance) {}

    public record LoyaltyRedeemRequest(
            @NotNull UUID customerId,
            @Min(1) int pointsToRedeem,
            @NotNull BigDecimal orderTotal,
            @NotBlank String orderId
    ) {}

    public record LoyaltyRedeemResponse(BigDecimal newTotal, int remainingPoints, int redeemedPoints) {}

    public record LoyaltyBalanceResponse(int points) {}

    public record QrSessionRequest(@NotBlank String tableNumber, @NotBlank String sessionId) {}

    public record QrSessionActiveResponse(boolean active) {}

    public record WhatsAppOrderRequest(@NotBlank String phone, @NotBlank String orderId) {}

    public record WhatsAppReceiptRequest(@NotBlank String phone, @NotBlank String receiptLink, @NotBlank String reorderLink) {}

    public record WhatsAppStatusRequest(@NotBlank String phone, @NotBlank String status) {}

    public record WhatsAppSendResponse(boolean sent) {}
}
