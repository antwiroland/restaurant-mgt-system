package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentStatus;

import java.util.UUID;

public record PaymentInitiateResponse(
        UUID paymentId,
        PaymentStatus status,
        String paystackReference,
        String authorizationUrl,
        String message
) {
}
