package com.restaurantmanager.core.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentRetryRequest(
        @NotBlank String momoPhone,
        @NotBlank String idempotencyKey
) {
}
