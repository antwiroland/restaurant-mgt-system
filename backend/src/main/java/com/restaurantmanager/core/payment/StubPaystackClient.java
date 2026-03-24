package com.restaurantmanager.core.payment;

import java.math.BigDecimal;
import java.util.UUID;

public class StubPaystackClient implements PaystackClient {

    @Override
    public InitiateResult initiate(String idempotencyKey, BigDecimal amount, String currency, String momoPhone) {
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String url = "https://checkout.paystack.com/" + reference;
        return new InitiateResult(reference, url, "Approve payment on your phone");
    }

    @Override
    public VerifyResult verify(String reference) {
        return new VerifyResult("initiated", "Awaiting approval");
    }
}
