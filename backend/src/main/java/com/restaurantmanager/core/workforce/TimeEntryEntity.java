package com.restaurantmanager.core.workforce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_entries")
public class TimeEntryEntity {
    @Id
    private UUID id;

    /** Login of the employee when they have one (keeps the self-service /timeclock endpoints working). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "employee_id")
    private UUID employeeId;

    /** POS, SELF_SERVICE, MANAGER, DEVICE or CORRECTION (see hr.attendance.AttendanceSource). */
    @Column(nullable = false, length = 20)
    private String source = "SELF_SERVICE";

    @Column(name = "clock_out_source", length = 20)
    private String clockOutSource;

    @Column(name = "terminal_id", length = 80)
    private String terminalId;

    /** Clocked in without a matching published shift (allowed by policy, flagged for review). */
    @Column(nullable = false)
    private boolean unscheduled;

    @jakarta.persistence.Version
    private long version;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "clock_in_at", nullable = false)
    private Instant clockInAt;

    @Column(name = "clock_out_at")
    private Instant clockOutAt;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(length = 500)
    private String notes;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edit_reason", length = 500)
    private String editReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    /** Worked minutes excluding breaks; open entries count up to {@code now}. */
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getClockOutSource() { return clockOutSource; }
    public void setClockOutSource(String clockOutSource) { this.clockOutSource = clockOutSource; }
    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
    public boolean isUnscheduled() { return unscheduled; }
    public void setUnscheduled(boolean unscheduled) { this.unscheduled = unscheduled; }
    public long getVersion() { return version; }

    public long workedMinutes(Instant now) {
        Instant end = clockOutAt == null ? now : clockOutAt;
        return Math.max(0, Duration.between(clockInAt, end).toMinutes() - breakMinutes);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }
    public Instant getClockInAt() { return clockInAt; }
    public void setClockInAt(Instant clockInAt) { this.clockInAt = clockInAt; }
    public Instant getClockOutAt() { return clockOutAt; }
    public void setClockOutAt(Instant clockOutAt) { this.clockOutAt = clockOutAt; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getEditedBy() { return editedBy; }
    public void setEditedBy(UUID editedBy) { this.editedBy = editedBy; }
    public String getEditReason() { return editReason; }
    public void setEditReason(String editReason) { this.editReason = editReason; }
}
