package com.restaurantmanager.core.hr.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Pre-authorisation of overtime for an employee on a date; approved minutes pre-fill timesheet overtime approval. */
@Entity
@Table(name = "hr_overtime_requests")
public class OvertimeRequestEntity {
    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "work_date", nullable = false) private LocalDate workDate;
    @Column(nullable = false) private int minutes;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private OvertimeRequestStatus status = OvertimeRequestStatus.PENDING;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note", length = 500) private String decisionNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate v) { this.workDate = v; }
    public int getMinutes() { return minutes; }
    public void setMinutes(int v) { this.minutes = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public OvertimeRequestStatus getStatus() { return status; }
    public void setStatus(OvertimeRequestStatus v) { this.status = v; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID v) { this.requestedBy = v; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID v) { this.decidedBy = v; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant v) { this.decidedAt = v; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String v) { this.decisionNote = v; }
    public Instant getCreatedAt() { return createdAt; }
}
