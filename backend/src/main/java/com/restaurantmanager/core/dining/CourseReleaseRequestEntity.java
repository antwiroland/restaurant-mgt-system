package com.restaurantmanager.core.dining;

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
 * A guest's "bring the next course". One request is shown to the waiter and the cashier alike; whoever releases the
 * course approves it, so the other sees it is handled.
 */
@Entity
@Table(name = "course_release_requests")
public class CourseReleaseRequestEntity {
    public enum Status { PENDING, APPROVED, CANCELLED, EXPIRED }

    @Id
    private UUID id;

    @Column(name = "dining_order_id", nullable = false)
    private UUID diningOrderId;

    @Column(name = "dining_order_course_id", nullable = false)
    private UUID diningOrderCourseId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    /** The guest's ordering session on their phone, when known. */
    @Column(name = "session_ref", length = 120)
    private String sessionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handled_by_role", length = 20)
    private String handledByRole;

    @Column(name = "handled_at")
    private Instant handledAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (requestedAt == null) requestedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDiningOrderId() { return diningOrderId; }
    public void setDiningOrderId(UUID diningOrderId) { this.diningOrderId = diningOrderId; }
    public UUID getDiningOrderCourseId() { return diningOrderCourseId; }
    public void setDiningOrderCourseId(UUID diningOrderCourseId) { this.diningOrderCourseId = diningOrderCourseId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public UUID getTableId() { return tableId; }
    public void setTableId(UUID tableId) { this.tableId = tableId; }
    public String getSessionRef() { return sessionRef; }
    public void setSessionRef(String sessionRef) { this.sessionRef = sessionRef; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
    public UUID getHandledBy() { return handledBy; }
    public void setHandledBy(UUID handledBy) { this.handledBy = handledBy; }
    public String getHandledByRole() { return handledByRole; }
    public void setHandledByRole(String handledByRole) { this.handledByRole = handledByRole; }
    public Instant getHandledAt() { return handledAt; }
    public void setHandledAt(Instant handledAt) { this.handledAt = handledAt; }
}
