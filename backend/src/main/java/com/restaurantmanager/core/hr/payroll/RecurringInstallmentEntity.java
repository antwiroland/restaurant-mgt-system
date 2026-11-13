package com.restaurantmanager.core.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One instalment of a recurring deduction taken in a payroll run (removed when the run is recalculated or cancelled). */
@Entity
@Table(name = "hr_payroll_recurring_installments")
public class RecurringInstallmentEntity {
    @Id
    private UUID id;
    @Column(name = "deduction_id", nullable = false) private UUID deductionId;
    @Column(name = "run_id", nullable = false) private UUID runId;
    @Column(name = "payslip_id", nullable = false) private UUID payslipId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public static RecurringInstallmentEntity of(UUID deductionId, UUID runId, UUID payslipId, BigDecimal amount) {
        RecurringInstallmentEntity e = new RecurringInstallmentEntity();
        e.deductionId = deductionId;
        e.runId = runId;
        e.payslipId = payslipId;
        e.amount = amount;
        return e;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDeductionId() { return deductionId; }
    public UUID getRunId() { return runId; }
    public UUID getPayslipId() { return payslipId; }
    public BigDecimal getAmount() { return amount; }
}
