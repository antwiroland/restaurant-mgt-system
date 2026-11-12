package com.restaurantmanager.core.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** One decision on one step of a leave request. Append-only. */
@Entity
@Table(name = "hr_leave_approvals", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "step"}))
public class LeaveApprovalEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(nullable = false) private int step;
    @Column(name = "step_code", nullable = false, length = 30) private String stepCode;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(nullable = false, length = 20) private String decision;
    @Column(length = 500) private String note;
    @Column(name = "decided_at", nullable = false) private Instant decidedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getDecidedAt() { return decidedAt; }
}
