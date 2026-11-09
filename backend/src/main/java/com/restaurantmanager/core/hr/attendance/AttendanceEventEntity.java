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

/** Append-only record of a clock action as received, keyed by the client's idempotency key. */
@Entity
@Table(name = "hr_attendance_events")
public class AttendanceEventEntity {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private ClockAction eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "terminal_id", length = 80)
    private String terminalId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "time_entry_id")
    private UUID timeEntryId;

    /** ACCEPTED or REJECTED. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(length = 300)
    private String message;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public ClockAction getEventType() { return eventType; }
    public void setEventType(ClockAction eventType) { this.eventType = eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public UUID getTimeEntryId() { return timeEntryId; }
    public void setTimeEntryId(UUID timeEntryId) { this.timeEntryId = timeEntryId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
}
