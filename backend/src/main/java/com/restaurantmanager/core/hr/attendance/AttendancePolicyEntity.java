package com.restaurantmanager.core.hr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Attendance rules for a branch, or the organisation default when branchId is null. */
@Entity
@Table(name = "hr_attendance_policies")
public class AttendancePolicyEntity {
    @Id
    private UUID id;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes;

    @Column(name = "early_leave_grace_minutes", nullable = false)
    private int earlyLeaveGraceMinutes;

    @Column(name = "early_clock_in_minutes", nullable = false)
    private int earlyClockInMinutes;

    @Column(name = "unscheduled_clock_in", nullable = false, length = 10)
    private String unscheduledClockIn;

    @Column(name = "require_correction_approval", nullable = false)
    private boolean requireCorrectionApproval;

    @Column(name = "meal_break_paid", nullable = false)
    private boolean mealBreakPaid;

    @Column(name = "rest_break_paid", nullable = false)
    private boolean restBreakPaid;

    @Column(name = "allow_self_clock", nullable = false)
    private boolean allowSelfClock;

    @Column(name = "missed_clock_out_minutes", nullable = false)
    private int missedClockOutMinutes;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public int getLateGraceMinutes() { return lateGraceMinutes; }
    public void setLateGraceMinutes(int v) { this.lateGraceMinutes = v; }
    public int getEarlyLeaveGraceMinutes() { return earlyLeaveGraceMinutes; }
    public void setEarlyLeaveGraceMinutes(int v) { this.earlyLeaveGraceMinutes = v; }
    public int getEarlyClockInMinutes() { return earlyClockInMinutes; }
    public void setEarlyClockInMinutes(int v) { this.earlyClockInMinutes = v; }
    public String getUnscheduledClockIn() { return unscheduledClockIn; }
    public void setUnscheduledClockIn(String v) { this.unscheduledClockIn = v; }
    public boolean isRequireCorrectionApproval() { return requireCorrectionApproval; }
    public void setRequireCorrectionApproval(boolean v) { this.requireCorrectionApproval = v; }
    public boolean isMealBreakPaid() { return mealBreakPaid; }
    public void setMealBreakPaid(boolean v) { this.mealBreakPaid = v; }
    public boolean isRestBreakPaid() { return restBreakPaid; }
    public void setRestBreakPaid(boolean v) { this.restBreakPaid = v; }
    public boolean isAllowSelfClock() { return allowSelfClock; }
    public void setAllowSelfClock(boolean v) { this.allowSelfClock = v; }
    public int getMissedClockOutMinutes() { return missedClockOutMinutes; }
    public void setMissedClockOutMinutes(int v) { this.missedClockOutMinutes = v; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
