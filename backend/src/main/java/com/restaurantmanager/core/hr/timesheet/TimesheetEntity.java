package com.restaurantmanager.core.hr.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One employee's timesheet for a period: a snapshot of attendance totals, moved through review and approval. */
@Entity
@Table(name = "hr_timesheets")
public class TimesheetEntity {
    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimesheetStatus status = TimesheetStatus.DRAFT;

    @Column(name = "scheduled_minutes", nullable = false) private int scheduledMinutes;
    @Column(name = "worked_minutes", nullable = false) private int workedMinutes;
    @Column(name = "unpaid_break_minutes", nullable = false) private int unpaidBreakMinutes;
    @Column(name = "paid_minutes", nullable = false) private int paidMinutes;
    @Column(name = "regular_minutes", nullable = false) private int regularMinutes;
    @Column(name = "daily_overtime_minutes", nullable = false) private int dailyOvertimeMinutes;
    @Column(name = "weekly_overtime_minutes", nullable = false) private int weeklyOvertimeMinutes;
    @Column(name = "overtime_computed_minutes", nullable = false) private int overtimeComputedMinutes;
    @Column(name = "overtime_approved_minutes") private Integer overtimeApprovedMinutes;
    @Column(name = "overtime_requires_approval", nullable = false) private boolean overtimeRequiresApproval = true;
    @Column(name = "holiday_minutes", nullable = false) private int holidayMinutes;
    @Column(name = "weekend_minutes", nullable = false) private int weekendMinutes;
    @Column(name = "night_minutes", nullable = false) private int nightMinutes;
    @Column(name = "late_count", nullable = false) private int lateCount;
    @Column(name = "absence_count", nullable = false) private int absenceCount;

    @Column(name = "rule_snapshot", columnDefinition = "TEXT")
    private String ruleSnapshot;

    @Column(length = 1000)
    private String notes;

    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "submitted_by") private UUID submittedBy;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "locked_by") private UUID lockedBy;
    @Column(name = "locked_at") private Instant lockedAt;

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

    /** Overtime that may be paid: the approved figure, or everything computed when the rules do not require approval. */
    public int payableOvertimeMinutes() {
        if (!overtimeRequiresApproval) {
            return overtimeComputedMinutes;
        }
        return overtimeApprovedMinutes == null ? 0 : overtimeApprovedMinutes;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate v) { this.periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate v) { this.periodEnd = v; }
    public TimesheetStatus getStatus() { return status; }
    public void setStatus(TimesheetStatus v) { this.status = v; }
    public int getScheduledMinutes() { return scheduledMinutes; }
    public void setScheduledMinutes(int v) { this.scheduledMinutes = v; }
    public int getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(int v) { this.workedMinutes = v; }
    public int getUnpaidBreakMinutes() { return unpaidBreakMinutes; }
    public void setUnpaidBreakMinutes(int v) { this.unpaidBreakMinutes = v; }
    public int getPaidMinutes() { return paidMinutes; }
    public void setPaidMinutes(int v) { this.paidMinutes = v; }
    public int getRegularMinutes() { return regularMinutes; }
    public void setRegularMinutes(int v) { this.regularMinutes = v; }
    public int getDailyOvertimeMinutes() { return dailyOvertimeMinutes; }
    public void setDailyOvertimeMinutes(int v) { this.dailyOvertimeMinutes = v; }
    public int getWeeklyOvertimeMinutes() { return weeklyOvertimeMinutes; }
    public void setWeeklyOvertimeMinutes(int v) { this.weeklyOvertimeMinutes = v; }
    public int getOvertimeComputedMinutes() { return overtimeComputedMinutes; }
    public void setOvertimeComputedMinutes(int v) { this.overtimeComputedMinutes = v; }
    public Integer getOvertimeApprovedMinutes() { return overtimeApprovedMinutes; }
    public void setOvertimeApprovedMinutes(Integer v) { this.overtimeApprovedMinutes = v; }
    public boolean isOvertimeRequiresApproval() { return overtimeRequiresApproval; }
    public void setOvertimeRequiresApproval(boolean v) { this.overtimeRequiresApproval = v; }
    public int getHolidayMinutes() { return holidayMinutes; }
    public void setHolidayMinutes(int v) { this.holidayMinutes = v; }
    public int getWeekendMinutes() { return weekendMinutes; }
    public void setWeekendMinutes(int v) { this.weekendMinutes = v; }
    public int getNightMinutes() { return nightMinutes; }
    public void setNightMinutes(int v) { this.nightMinutes = v; }
    public int getLateCount() { return lateCount; }
    public void setLateCount(int v) { this.lateCount = v; }
    public int getAbsenceCount() { return absenceCount; }
    public void setAbsenceCount(int v) { this.absenceCount = v; }
    public String getRuleSnapshot() { return ruleSnapshot; }
    public void setRuleSnapshot(String v) { this.ruleSnapshot = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant v) { this.generatedAt = v; }
    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID v) { this.submittedBy = v; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant v) { this.submittedAt = v; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID v) { this.reviewedBy = v; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public UUID getLockedBy() { return lockedBy; }
    public void setLockedBy(UUID v) { this.lockedBy = v; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant v) { this.lockedAt = v; }
    public long getVersion() { return version; }
}
