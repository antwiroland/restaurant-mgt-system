package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotNull PaymentMethod method,
        /** Payer phone: required for MOBILE_MONEY and CARD, not used for CASH. */
        String momoPhone,
        @NotBlank String idempotencyKey,
        /** Required when the order's bill has been split: the share being paid. */
        UUID splitId,
        /** CASH only: amount handed over; the change due is returned. */
        @jakarta.validation.constraints.DecimalMin("0.00") java.math.BigDecimal amountTendered
) {
    public PaymentInitiateRequest(UUID orderId, PaymentMethod method, String momoPhone, String idempotencyKey, UUID splitId) {
        this(orderId, method, momoPhone, idempotencyKey, splitId, null);
    }

    public PaymentInitiateRequest(UUID orderId, PaymentMethod method, String momoPhone, String idempotencyKey) {
        this(orderId, method, momoPhone, idempotencyKey, null);
    }
}
