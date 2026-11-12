package com.restaurantmanager.core.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A payroll run for one branch and period: Draft → Calculated → Approved → Finalized → Paid (or Cancelled before finalizing). */
@Entity
@Table(name = "hr_payroll_runs")
public class PayrollRunEntity {
    @Id
    private UUID id;

    @Column(name = "run_number", nullable = false, unique = true, length = 30) private String runNumber;
    @Column(name = "branch_id", nullable = false) private UUID branchId;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "pay_date") private LocalDate payDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private PayrollRunStatus status = PayrollRunStatus.DRAFT;
    @Column(nullable = false, length = 3) private String currency = "GHS";
    @Column(name = "employee_count", nullable = false) private int employeeCount;
    @Column(name = "gross_total", nullable = false, precision = 14, scale = 2) private BigDecimal grossTotal = BigDecimal.ZERO;
    @Column(name = "deductions_total", nullable = false, precision = 14, scale = 2) private BigDecimal deductionsTotal = BigDecimal.ZERO;
    @Column(name = "net_total", nullable = false, precision = 14, scale = 2) private BigDecimal netTotal = BigDecimal.ZERO;
    @Column(name = "employer_total", nullable = false, precision = 14, scale = 2) private BigDecimal employerTotal = BigDecimal.ZERO;
    @Column(length = 500) private String notes;
    @Column(columnDefinition = "TEXT") private String skipped;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "calculated_by") private UUID calculatedBy;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "finalized_by") private UUID finalizedBy;
    @Column(name = "finalized_at") private Instant finalizedAt;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
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
    public String getRunNumber() { return runNumber; }
    public void setRunNumber(String v) { this.runNumber = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate v) { this.periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate v) { this.periodEnd = v; }
    public LocalDate getPayDate() { return payDate; }
    public void setPayDate(LocalDate v) { this.payDate = v; }
    public PayrollRunStatus getStatus() { return status; }
    public void setStatus(PayrollRunStatus v) { this.status = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public int getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(int v) { this.employeeCount = v; }
    public BigDecimal getGrossTotal() { return grossTotal; }
    public void setGrossTotal(BigDecimal v) { this.grossTotal = v; }
    public BigDecimal getDeductionsTotal() { return deductionsTotal; }
    public void setDeductionsTotal(BigDecimal v) { this.deductionsTotal = v; }
    public BigDecimal getNetTotal() { return netTotal; }
    public void setNetTotal(BigDecimal v) { this.netTotal = v; }
    public BigDecimal getEmployerTotal() { return employerTotal; }
    public void setEmployerTotal(BigDecimal v) { this.employerTotal = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getSkipped() { return skipped; }
    public void setSkipped(String v) { this.skipped = v; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public UUID getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(UUID v) { this.calculatedBy = v; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant v) { this.calculatedAt = v; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public UUID getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(UUID v) { this.finalizedBy = v; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant v) { this.finalizedAt = v; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String v) { this.cancelReason = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
