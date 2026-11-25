package com.restaurantmanager.core.dining;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * One course of one meal. The course's name and position are copied when it is first ordered, so renaming or
 * reordering the course configuration later never changes what past meals show.
 */
@Entity
@Table(name = "dining_order_courses",
        uniqueConstraints = @UniqueConstraint(name = "uq_dining_order_course", columnNames = {"dining_order_id", "course_id"}))
public class DiningOrderCourseEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dining_order_id", nullable = false)
    private DiningOrderEntity diningOrder;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "course_name", nullable = false, length = 80)
    private String courseName;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status = CourseStatus.PENDING;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @Column(name = "released_by_name", length = 120)
    private String releasedByName;

    @Column(name = "released_by_role", length = 20)
    private String releasedByRole;

    @Column(name = "served_at")
    private Instant servedAt;

    /** Optimistic lock: a second, stale release attempt fails instead of overwriting the first. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isReleased() {
        return releasedAt != null;
    }

    public UUID getId() { return id; }
    public DiningOrderEntity getDiningOrder() { return diningOrder; }
    public void setDiningOrder(DiningOrderEntity diningOrder) { this.diningOrder = diningOrder; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public CourseStatus getStatus() { return status; }
    public void setStatus(CourseStatus status) { this.status = status; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public UUID getReleasedBy() { return releasedBy; }
    public void setReleasedBy(UUID releasedBy) { this.releasedBy = releasedBy; }
    public String getReleasedByName() { return releasedByName; }
    public void setReleasedByName(String releasedByName) { this.releasedByName = releasedByName; }
    public String getReleasedByRole() { return releasedByRole; }
    public void setReleasedByRole(String releasedByRole) { this.releasedByRole = releasedByRole; }
    public Instant getServedAt() { return servedAt; }
    public void setServedAt(Instant servedAt) { this.servedAt = servedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
