package com.restaurantmanager.core.hr.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An employee's request to hand a shift to a colleague (optionally taking one of the colleague's shifts in return). */
@Entity
@Table(name = "hr_shift_swap_requests")
public class SwapRequestEntity {
    @Id
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "requester_employee_id", nullable = false)
    private UUID requesterEmployeeId;

    @Column(name = "colleague_employee_id", nullable = false)
    private UUID colleagueEmployeeId;

    @Column(name = "colleague_schedule_id")
    private UUID colleagueScheduleId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SwapStatus status = SwapStatus.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_by")
    private UUID createdBy;

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
    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }
    public UUID getRequesterEmployeeId() { return requesterEmployeeId; }
    public void setRequesterEmployeeId(UUID requesterEmployeeId) { this.requesterEmployeeId = requesterEmployeeId; }
    public UUID getColleagueEmployeeId() { return colleagueEmployeeId; }
    public void setColleagueEmployeeId(UUID colleagueEmployeeId) { this.colleagueEmployeeId = colleagueEmployeeId; }
    public UUID getColleagueScheduleId() { return colleagueScheduleId; }
    public void setColleagueScheduleId(UUID colleagueScheduleId) { this.colleagueScheduleId = colleagueScheduleId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public SwapStatus getStatus() { return status; }
    public void setStatus(SwapStatus status) { this.status = status; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
