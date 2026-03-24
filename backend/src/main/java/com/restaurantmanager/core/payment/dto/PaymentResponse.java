package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String paystackReference,
        String authorizationUrl,
        String momoPhone,
        String providerMessage,
        Instant paidAt,
        Instant createdAt
) {
}
