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

/** One change of a course's status: from what, to what, by whom (null: the kitchen's progress or the system). */
@Entity
@Table(name = "course_status_history")
public class CourseStatusHistoryEntity {
    @Id
    private UUID id;

    @Column(name = "dining_order_course_id", nullable = false)
    private UUID diningOrderCourseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private CourseStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private CourseStatus toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_by_role", length = 20)
    private String changedByRole;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static CourseStatusHistoryEntity of(UUID courseId, CourseStatus from, CourseStatus to, UUID by, String role, String reason) {
        CourseStatusHistoryEntity entry = new CourseStatusHistoryEntity();
        entry.diningOrderCourseId = courseId;
        entry.fromStatus = from;
        entry.toStatus = to;
        entry.changedBy = by;
        entry.changedByRole = role;
        entry.reason = reason;
        return entry;
    }

    public UUID getId() { return id; }
    public UUID getDiningOrderCourseId() { return diningOrderCourseId; }
    public CourseStatus getFromStatus() { return fromStatus; }
    public CourseStatus getToStatus() { return toStatus; }
    public UUID getChangedBy() { return changedBy; }
    public String getChangedByRole() { return changedByRole; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
