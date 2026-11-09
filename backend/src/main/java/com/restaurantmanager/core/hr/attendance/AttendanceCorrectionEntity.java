package com.restaurantmanager.core.hr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A change to attendance with the values before and after. Time entries are only changed through applied corrections,
 * so the original clock times always remain on record.
 */
@Entity
@Table(name = "hr_attendance_corrections")
public class AttendanceCorrectionEntity {
    @Id
    private UUID id;

    /** Null until applied when the correction adds a missing entry. */
    @Column(name = "time_entry_id")
    private UUID timeEntryId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "original_clock_in")
    private Instant originalClockIn;

    @Column(name = "original_clock_out")
    private Instant originalClockOut;

    @Column(name = "original_break_minutes")
    private Integer originalBreakMinutes;

    @Column(name = "corrected_clock_in", nullable = false)
    private Instant correctedClockIn;

    @Column(name = "corrected_clock_out")
    private Instant correctedClockOut;

    @Column(name = "corrected_break_minutes", nullable = false)
    private int correctedBreakMinutes;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionStatus status;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTimeEntryId() { return timeEntryId; }
    public void setTimeEntryId(UUID timeEntryId) { this.timeEntryId = timeEntryId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public Instant getOriginalClockIn() { return originalClockIn; }
    public void setOriginalClockIn(Instant originalClockIn) { this.originalClockIn = originalClockIn; }
    public Instant getOriginalClockOut() { return originalClockOut; }
    public void setOriginalClockOut(Instant originalClockOut) { this.originalClockOut = originalClockOut; }
    public Integer getOriginalBreakMinutes() { return originalBreakMinutes; }
    public void setOriginalBreakMinutes(Integer originalBreakMinutes) { this.originalBreakMinutes = originalBreakMinutes; }
    public Instant getCorrectedClockIn() { return correctedClockIn; }
    public void setCorrectedClockIn(Instant correctedClockIn) { this.correctedClockIn = correctedClockIn; }
    public Instant getCorrectedClockOut() { return correctedClockOut; }
    public void setCorrectedClockOut(Instant correctedClockOut) { this.correctedClockOut = correctedClockOut; }
    public int getCorrectedBreakMinutes() { return correctedBreakMinutes; }
    public void setCorrectedBreakMinutes(int correctedBreakMinutes) { this.correctedBreakMinutes = correctedBreakMinutes; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public CorrectionStatus getStatus() { return status; }
    public void setStatus(CorrectionStatus status) { this.status = status; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
