package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotNull PaymentMethod method,
        @NotBlank String momoPhone,
        @NotBlank String idempotencyKey,
        /** Required when the order's bill has been split: the share being paid. */
        UUID splitId
) {
    public PaymentInitiateRequest(UUID orderId, PaymentMethod method, String momoPhone, String idempotencyKey) {
        this(orderId, method, momoPhone, idempotencyKey, null);
    }
}
