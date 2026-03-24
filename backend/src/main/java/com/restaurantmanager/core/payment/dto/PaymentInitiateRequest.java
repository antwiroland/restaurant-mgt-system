package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotNull PaymentMethod method,
        @NotBlank String momoPhone,
        @NotBlank String idempotencyKey
) {
}
