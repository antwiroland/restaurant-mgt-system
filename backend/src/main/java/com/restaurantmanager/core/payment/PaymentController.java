package com.restaurantmanager.core.payment;

import com.restaurantmanager.core.payment.dto.PaymentInitiateRequest;
import com.restaurantmanager.core.payment.dto.PaymentInitiateResponse;
import com.restaurantmanager.core.payment.dto.PaymentResponse;
import com.restaurantmanager.core.payment.dto.PaymentRetryRequest;
import com.restaurantmanager.core.payment.dto.ReceiptResponse;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PaymentInitiateResponse initiate(@Valid @RequestBody PaymentInitiateRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.initiate(request, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PaymentResponse get(@PathVariable UUID id,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.get(id, principal);
    }

    @GetMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PaymentResponse verify(@PathVariable UUID id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.verify(id, principal);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PaymentInitiateResponse retry(@PathVariable UUID id,
                                         @Valid @RequestBody PaymentRetryRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.retry(id, request, principal);
    }

    @PostMapping("/webhook")
    public Map<String, String> webhook(@RequestHeader(value = "X-Paystack-Signature", required = false) String signature,
                                       @RequestBody String payload) {
        paymentService.processWebhook(signature, payload);
        return paymentService.webhookOkResponse();
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public ReceiptResponse receiptByPayment(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.receiptByPaymentId(id, principal);
    }

    @GetMapping("/orders/{orderId}/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public ReceiptResponse receiptByOrder(@PathVariable UUID orderId,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.receiptByOrderId(orderId, principal);
    }
}
