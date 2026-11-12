package com.restaurantmanager.core.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One employee's balance for one leave type and calendar year. Every change also writes a {@link LeaveTransactionEntity},
 * so the figures here are a cache of the ledger that can be explained line by line.
 */
@Entity
@Table(name = "hr_leave_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "leave_year"}))
public class LeaveBalanceEntity {
    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Column(name = "leave_year", nullable = false) private int leaveYear;
    @Column(name = "entitled_days", nullable = false, precision = 6, scale = 2) private BigDecimal entitledDays = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private Accrual accrual = Accrual.UPFRONT;
    @Column(name = "accrual_start") private LocalDate accrualStart;
    @Column(name = "carried_over_days", nullable = false, precision = 6, scale = 2) private BigDecimal carriedOverDays = BigDecimal.ZERO;
    @Column(name = "adjustment_days", nullable = false, precision = 6, scale = 2) private BigDecimal adjustmentDays = BigDecimal.ZERO;
    @Column(name = "used_days", nullable = false, precision = 6, scale = 2) private BigDecimal usedDays = BigDecimal.ZERO;
    @Column(name = "pending_days", nullable = false, precision = 6, scale = 2) private BigDecimal pendingDays = BigDecimal.ZERO;
    @Column(name = "policy_id") private UUID policyId;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public UUID getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(UUID leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public int getLeaveYear() { return leaveYear; }
    public void setLeaveYear(int leaveYear) { this.leaveYear = leaveYear; }
    public BigDecimal getEntitledDays() { return entitledDays; }
    public void setEntitledDays(BigDecimal entitledDays) { this.entitledDays = entitledDays; }
    public Accrual getAccrual() { return accrual; }
    public void setAccrual(Accrual accrual) { this.accrual = accrual; }
    public LocalDate getAccrualStart() { return accrualStart; }
    public void setAccrualStart(LocalDate accrualStart) { this.accrualStart = accrualStart; }
    public BigDecimal getCarriedOverDays() { return carriedOverDays; }
    public void setCarriedOverDays(BigDecimal carriedOverDays) { this.carriedOverDays = carriedOverDays; }
    public BigDecimal getAdjustmentDays() { return adjustmentDays; }
    public void setAdjustmentDays(BigDecimal adjustmentDays) { this.adjustmentDays = adjustmentDays; }
    public BigDecimal getUsedDays() { return usedDays; }
    public void setUsedDays(BigDecimal usedDays) { this.usedDays = usedDays; }
    public BigDecimal getPendingDays() { return pendingDays; }
    public void setPendingDays(BigDecimal pendingDays) { this.pendingDays = pendingDays; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
