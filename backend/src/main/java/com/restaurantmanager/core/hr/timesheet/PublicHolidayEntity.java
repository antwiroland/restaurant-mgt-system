package com.restaurantmanager.core.hr.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A public holiday, observed everywhere (branchId null) or at one branch. Used for holiday overtime and leave day counts. */
@Entity
@Table(name = "hr_public_holidays")
public class PublicHolidayEntity {
    @Id
    private UUID id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "branch_id")
    private UUID branchId;

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
    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
