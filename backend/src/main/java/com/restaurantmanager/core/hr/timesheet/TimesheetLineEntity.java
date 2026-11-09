package com.restaurantmanager.core.hr.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One day's attendance (or absence) as captured on a timesheet. */
@Entity
@Table(name = "hr_timesheet_lines")
public class TimesheetLineEntity {
    @Id
    private UUID id;

    @Column(name = "timesheet_id", nullable = false) private UUID timesheetId;
    @Column(name = "work_date", nullable = false) private LocalDate workDate;
    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "time_entry_id") private UUID timeEntryId;
    @Column(name = "schedule_id") private UUID scheduleId;
    @Column(name = "shift_start") private Instant shiftStart;
    @Column(name = "shift_end") private Instant shiftEnd;
    @Column(name = "clock_in") private Instant clockIn;
    @Column(name = "clock_out") private Instant clockOut;
    @Column(name = "scheduled_minutes", nullable = false) private int scheduledMinutes;
    @Column(name = "worked_minutes", nullable = false) private int workedMinutes;
    @Column(name = "unpaid_break_minutes", nullable = false) private int unpaidBreakMinutes;
    @Column(name = "paid_minutes", nullable = false) private int paidMinutes;
    @Column(name = "late_minutes", nullable = false) private int lateMinutes;
    @Column(name = "early_leave_minutes", nullable = false) private int earlyLeaveMinutes;
    @Column(length = 120) private String flags;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public UUID getTimesheetId() { return timesheetId; }
    public void setTimesheetId(UUID v) { this.timesheetId = v; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate v) { this.workDate = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public UUID getTimeEntryId() { return timeEntryId; }
    public void setTimeEntryId(UUID v) { this.timeEntryId = v; }
    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID v) { this.scheduleId = v; }
    public Instant getShiftStart() { return shiftStart; }
    public void setShiftStart(Instant v) { this.shiftStart = v; }
    public Instant getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(Instant v) { this.shiftEnd = v; }
    public Instant getClockIn() { return clockIn; }
    public void setClockIn(Instant v) { this.clockIn = v; }
    public Instant getClockOut() { return clockOut; }
    public void setClockOut(Instant v) { this.clockOut = v; }
    public int getScheduledMinutes() { return scheduledMinutes; }
    public void setScheduledMinutes(int v) { this.scheduledMinutes = v; }
    public int getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(int v) { this.workedMinutes = v; }
    public int getUnpaidBreakMinutes() { return unpaidBreakMinutes; }
    public void setUnpaidBreakMinutes(int v) { this.unpaidBreakMinutes = v; }
    public int getPaidMinutes() { return paidMinutes; }
    public void setPaidMinutes(int v) { this.paidMinutes = v; }
    public int getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(int v) { this.lateMinutes = v; }
    public int getEarlyLeaveMinutes() { return earlyLeaveMinutes; }
    public void setEarlyLeaveMinutes(int v) { this.earlyLeaveMinutes = v; }
    public String getFlags() { return flags; }
    public void setFlags(String v) { this.flags = v; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { this.sortOrder = v; }
}
