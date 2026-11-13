package com.restaurantmanager.core.hr.leave;

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
import java.util.List;
import java.util.UUID;

/**
 * A leave request. {@code approvalSteps} is the chain resolved when the request was made (later changes to the leave type
 * do not affect it); {@code currentStep} indexes the step waiting for a decision.
 */
@Entity
@Table(name = "hr_leave_requests")
public class LeaveRequestEntity {
    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "start_half", nullable = false) private boolean startHalf;
    @Column(name = "end_half", nullable = false) private boolean endHalf;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal days;
    @Column(length = 500) private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private LeaveStatus status = LeaveStatus.PENDING;
    @Column(name = "approval_steps", nullable = false, length = 80) private String approvalSteps;
    @Column(name = "current_step", nullable = false) private int currentStep;
    @Column(name = "document_id") private UUID documentId;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
    @Column(name = "decided_at") private Instant decidedAt;
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

    public List<ApprovalStep> steps() {
        return LeaveTypeEntity.parseSteps(approvalSteps);
    }

    /** The step waiting for a decision, or null when the request is no longer pending. */
    public ApprovalStep pendingStep() {
        List<ApprovalStep> steps = steps();
        return status == LeaveStatus.PENDING && currentStep < steps.size() ? steps.get(currentStep) : null;
    }

    /** Whether {@code day} is a leave day of this request (dates are inclusive). */
    public boolean covers(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public UUID getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(UUID leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isStartHalf() { return startHalf; }
    public void setStartHalf(boolean startHalf) { this.startHalf = startHalf; }
    public boolean isEndHalf() { return endHalf; }
    public void setEndHalf(boolean endHalf) { this.endHalf = endHalf; }
    public BigDecimal getDays() { return days; }
    public void setDays(BigDecimal days) { this.days = days; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }
    public String getApprovalSteps() { return approvalSteps; }
    public void setApprovalSteps(String approvalSteps) { this.approvalSteps = approvalSteps; }
    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** Of {@link #getDays()}, the days that fall in the following year (leave running over New Year). */
    @Column(name = "next_year_days", nullable = false, precision = 6, scale = 2) private BigDecimal nextYearDays = BigDecimal.ZERO;

    public BigDecimal getNextYearDays() { return nextYearDays; }
    public void setNextYearDays(BigDecimal nextYearDays) { this.nextYearDays = nextYearDays; }

    /** Days charged to each year: {startYear: days − nextYearDays, startYear + 1: nextYearDays}, leaving out zero. */
    public java.util.Map<Integer, BigDecimal> daysByYear() {
        java.util.Map<Integer, BigDecimal> map = new java.util.LinkedHashMap<>();
        BigDecimal next = nextYearDays == null ? BigDecimal.ZERO : nextYearDays;
        BigDecimal first = days.subtract(next);
        if (first.signum() > 0) map.put(startDate.getYear(), first);
        if (next.signum() > 0) map.put(startDate.getYear() + 1, next);
        return map;
    }
}
