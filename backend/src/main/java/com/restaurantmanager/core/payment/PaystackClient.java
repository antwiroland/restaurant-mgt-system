package com.restaurantmanager.core.payment;

import java.math.BigDecimal;

public interface PaystackClient {
    InitiateResult initiate(String idempotencyKey, BigDecimal amount, String currency, String momoPhone);

    VerifyResult verify(String reference);

    RefundResult refund(String reference, BigDecimal amount, String currency);

    record InitiateResult(String reference, String authorizationUrl, String message) {
    }

    record VerifyResult(String providerStatus, String message) {
    }

    /**
     * @param providerStatus Paystack refund status (e.g. pending, processing, processed, failed)
     */
    record RefundResult(String providerReference, String providerStatus, String message) {
        public boolean failed() {
            return "failed".equalsIgnoreCase(providerStatus);
        }
    }
}
