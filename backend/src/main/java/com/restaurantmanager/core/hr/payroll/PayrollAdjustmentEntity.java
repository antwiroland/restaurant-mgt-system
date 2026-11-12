package com.restaurantmanager.core.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A one-off earning (bonus, allowance) or deduction (advance repayment) for an employee in a run. */
@Entity
@Table(name = "hr_payroll_adjustments")
public class PayrollAdjustmentEntity {
    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false) private UUID runId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private AdjustmentKind kind;
    @Column(nullable = false) private boolean taxable = true;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID v) { this.runId = v; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public AdjustmentKind getKind() { return kind; }
    public void setKind(AdjustmentKind v) { this.kind = v; }
    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean v) { this.taxable = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
