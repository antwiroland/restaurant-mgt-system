package com.restaurantmanager.core.phase9.refund;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentRecord {
    private final UUID id;
    private final BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private PaymentRecordStatus status;

    public PaymentRecord(UUID id, BigDecimal paidAmount) {
        this.id = id;
        this.paidAmount = paidAmount;
        this.refundedAmount = BigDecimal.ZERO;
        this.status = PaymentRecordStatus.SUCCESS;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public PaymentRecordStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentRecordStatus status) {
        this.status = status;
    }
}
