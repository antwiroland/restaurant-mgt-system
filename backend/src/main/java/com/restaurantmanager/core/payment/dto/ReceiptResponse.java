package com.restaurantmanager.core.payment.dto;

import com.restaurantmanager.core.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        String receiptNumber,
        UUID paymentId,
        UUID orderId,
        BigDecimal subtotal,
        BigDecimal total,
        String currency,
        PaymentMethod paymentMethod,
        Instant paidAt,
        BigDecimal discountTotal,
        BigDecimal deliveryFee,
        BigDecimal serviceCharge,
        BigDecimal taxTotal,
        boolean taxInclusive,
        java.util.List<com.restaurantmanager.core.billing.BillingService.TaxLine> taxLines,
        BigDecimal tipAmount,
        BigDecimal amountPaid,
        BigDecimal refundedAmount
) {
}
