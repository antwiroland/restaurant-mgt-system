package com.restaurantmanager.core.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Offline Paystack client used in development/tests (payments.paystack.mode=stub).
 */
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

    @Override
    public RefundResult refund(String reference, BigDecimal amount, String currency) {
        return new RefundResult("RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "processed", "Refund processed");
    }
}
