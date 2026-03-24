package com.restaurantmanager.core.payment;

import java.math.BigDecimal;

public interface PaystackClient {
    InitiateResult initiate(String idempotencyKey, BigDecimal amount, String currency, String momoPhone);

    VerifyResult verify(String reference);

    record InitiateResult(String reference, String authorizationUrl, String message) {
    }

    record VerifyResult(String providerStatus, String message) {
    }
}
