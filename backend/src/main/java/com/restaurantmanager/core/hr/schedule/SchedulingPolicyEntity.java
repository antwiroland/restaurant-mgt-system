package com.restaurantmanager.core.hr.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Scheduling limits for a branch (or the organisation default when branchId is null). */
@Entity
@Table(name = "hr_scheduling_policies")
public class SchedulingPolicyEntity {
    @Id
    private UUID id;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "min_rest_minutes", nullable = false)
    private int minRestMinutes;

    @Column(name = "max_shift_minutes", nullable = false)
    private int maxShiftMinutes;

    @Column(name = "max_weekly_minutes", nullable = false)
    private int maxWeeklyMinutes;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public int getMinRestMinutes() { return minRestMinutes; }
    public void setMinRestMinutes(int minRestMinutes) { this.minRestMinutes = minRestMinutes; }
    public int getMaxShiftMinutes() { return maxShiftMinutes; }
    public void setMaxShiftMinutes(int maxShiftMinutes) { this.maxShiftMinutes = maxShiftMinutes; }
    public int getMaxWeeklyMinutes() { return maxWeeklyMinutes; }
    public void setMaxWeeklyMinutes(int maxWeeklyMinutes) { this.maxWeeklyMinutes = maxWeeklyMinutes; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
