package com.restaurantmanager.core.hr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A break within a time entry. Whether it is paid is fixed from the policy when the break starts. */
@Entity
@Table(name = "hr_attendance_breaks")
public class AttendanceBreakEntity {
    @Id
    private UUID id;

    @Column(name = "time_entry_id", nullable = false)
    private UUID timeEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_type", nullable = false, length = 20)
    private BreakType breakType;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public long minutes(Instant now) {
        return Duration.between(startedAt, endedAt != null ? endedAt : now).toMinutes();
    }

    public UUID getId() { return id; }
    public UUID getTimeEntryId() { return timeEntryId; }
    public void setTimeEntryId(UUID timeEntryId) { this.timeEntryId = timeEntryId; }
    public BreakType getBreakType() { return breakType; }
    public void setBreakType(BreakType breakType) { this.breakType = breakType; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
