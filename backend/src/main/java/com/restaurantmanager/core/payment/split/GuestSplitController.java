package com.restaurantmanager.core.payment.split;

import com.restaurantmanager.core.payment.PaymentService;
import com.restaurantmanager.core.payment.dto.PaymentInitiateResponse;
import com.restaurantmanager.core.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public split-bill endpoints for dine-in guests on the table QR page. Access is scoped by the table's QR token:
 * the order must have been placed at that table and still be open.
 */
@RestController
@RequestMapping("/orders/public/dine-in/tables/{tableToken}/orders/{orderId}")
public class GuestSplitController {
    private final SplitService splitService;
    private final PaymentService paymentService;

    public GuestSplitController(SplitService splitService, PaymentService paymentService) {
        this.splitService = splitService;
        this.paymentService = paymentService;
    }

    @GetMapping("/splits")
    public List<SplitService.SplitView> list(@PathVariable String tableToken, @PathVariable UUID orderId) {
        return splitService.listAsGuest(tableToken, orderId);
    }

    @PostMapping("/splits")
    public List<SplitService.SplitView> split(@PathVariable String tableToken, @PathVariable UUID orderId,
                                              @Valid @RequestBody GuestSplitRequest request) {
        return splitService.createSplitsAsGuest(tableToken, orderId, request.parts(), request.names());
    }

    @PostMapping("/splits/{splitId}/pay")
    public PaymentInitiateResponse pay(@PathVariable String tableToken, @PathVariable UUID orderId, @PathVariable UUID splitId,
                                       @Valid @RequestBody GuestPayRequest request) {
        return paymentService.initiateGuestShare(tableToken, orderId, splitId, request.momoPhone(), request.idempotencyKey());
    }

    /** Pay the whole order (bill not split) by Mobile Money. */
    @PostMapping("/pay")
    public PaymentInitiateResponse payWhole(@PathVariable String tableToken, @PathVariable UUID orderId,
                                            @Valid @RequestBody GuestPayRequest request) {
        return paymentService.initiateGuestFull(tableToken, orderId, request.momoPhone(), request.idempotencyKey());
    }

    @PostMapping("/payments/{paymentId}/verify")
    public PaymentResponse verify(@PathVariable String tableToken, @PathVariable UUID orderId, @PathVariable UUID paymentId) {
        return paymentService.verifyGuestPayment(tableToken, orderId, paymentId);
    }

    public record GuestSplitRequest(@NotNull @Min(2) @Max(20) Integer parts, @Size(max = 20) List<@Size(max = 60) String> names) {}

    public record GuestPayRequest(@NotBlank @Size(max = 20) String momoPhone, @NotBlank @Size(max = 100) String idempotencyKey) {}
}
