package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.hr.employee.EmploymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated entitlement for a leave type, optionally narrowed to a branch and/or an employment type. Policies are
 * never edited: a change is a new row with a later {@code effectiveFrom}.
 */
@Entity
@Table(name = "hr_leave_policies")
public class LeavePolicyEntity {
    @Id
    private UUID id;

    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Column(name = "branch_id") private UUID branchId;
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20) private EmploymentType employmentType;
    @Column(name = "annual_days", nullable = false, precision = 6, scale = 2) private BigDecimal annualDays;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private Accrual accrual;
    @Column(name = "carry_over_max_days", nullable = false, precision = 6, scale = 2) private BigDecimal carryOverMaxDays = BigDecimal.ZERO;
    @Column(name = "min_service_days", nullable = false) private int minServiceDays;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    /** 0 = organisation-wide; employment type +1; branch +2. Higher wins. */
    public int specificity() {
        return (branchId == null ? 0 : 2) + (employmentType == null ? 0 : 1);
    }

    public UUID getId() { return id; }
    public UUID getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(UUID leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public BigDecimal getAnnualDays() { return annualDays; }
    public void setAnnualDays(BigDecimal annualDays) { this.annualDays = annualDays; }
    public Accrual getAccrual() { return accrual; }
    public void setAccrual(Accrual accrual) { this.accrual = accrual; }
    public BigDecimal getCarryOverMaxDays() { return carryOverMaxDays; }
    public void setCarryOverMaxDays(BigDecimal carryOverMaxDays) { this.carryOverMaxDays = carryOverMaxDays; }
    public int getMinServiceDays() { return minServiceDays; }
    public void setMinServiceDays(int minServiceDays) { this.minServiceDays = minServiceDays; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
