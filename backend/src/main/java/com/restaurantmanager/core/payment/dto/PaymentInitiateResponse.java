package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentStatus;

import java.util.UUID;

public record PaymentInitiateResponse(
        UUID paymentId,
        PaymentStatus status,
        String paystackReference,
        String authorizationUrl,
        String message,
        /** CASH only: change to hand back (null for other methods). */
        java.math.BigDecimal changeDue
) {
    public PaymentInitiateResponse(UUID paymentId, PaymentStatus status, String paystackReference,
                                   String authorizationUrl, String message) {
        this(paymentId, status, paystackReference, authorizationUrl, message, null);
    }

}
