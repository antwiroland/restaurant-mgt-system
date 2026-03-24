package com.restaurantmanager.core.phase9.refund;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.phase9.audit.FinancialAuditEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RefundService {
    public PaymentRecord refund(PaymentRecord payment,
                                BigDecimal amount,
                                boolean overrideTokenValid,
                                UUID actorId,
                                List<FinancialAuditEvent> auditSink) {
        if (!overrideTokenValid) {
            throw new ApiException(403, "Override token required");
        }
        if (payment.getStatus() == PaymentRecordStatus.REFUNDED) {
            throw new ApiException(400, "Payment already refunded");
        }

        BigDecimal remaining = payment.getPaidAmount().subtract(payment.getRefundedAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new ApiException(400, "Refund exceeds available amount");
        }

        BigDecimal newRefunded = payment.getRefundedAmount().add(amount);
        payment.setRefundedAmount(newRefunded);
        if (newRefunded.compareTo(payment.getPaidAmount()) == 0) {
            payment.setStatus(PaymentRecordStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentRecordStatus.PARTIALLY_REFUNDED);
        }

        auditSink.add(new FinancialAuditEvent(actorId, AuditAction.PAYMENT_REFUNDED, Instant.now(),
                "refund=" + amount));
        return payment;
    }
}
