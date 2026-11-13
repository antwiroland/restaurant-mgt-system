package com.restaurantmanager.core.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A loan, salary advance or other deduction taken from every pay run from {@code startDate}: the instalment each time,
 * until {@code totalAmount} is repaid (when set) or it is stopped.
 */
@Entity
@Table(name = "hr_payroll_recurring_deductions")
public class RecurringDeductionEntity {
    @Id
    private UUID id;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    /** LOAN, ADVANCE or OTHER. */
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false, length = 120) private String description;
    @Column(name = "installment_amount", nullable = false, precision = 14, scale = 2) private BigDecimal installmentAmount;
    @Column(name = "total_amount", precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "stopped_reason", length = 300) private String stoppedReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal installmentAmount) { this.installmentAmount = installmentAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getStoppedReason() { return stoppedReason; }
    public void setStoppedReason(String stoppedReason) { this.stoppedReason = stoppedReason; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
