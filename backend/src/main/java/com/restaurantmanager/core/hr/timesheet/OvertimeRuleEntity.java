package com.restaurantmanager.core.hr.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Overtime rule effective from a date, for a branch or (branchId null) the whole organisation. Rows are never edited. */
@Entity
@Table(name = "hr_overtime_rules")
public class OvertimeRuleEntity {
    @Id
    private UUID id;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "daily_threshold_minutes")
    private Integer dailyThresholdMinutes;

    @Column(name = "weekly_threshold_minutes")
    private Integer weeklyThresholdMinutes;

    @Column(name = "daily_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal dailyMultiplier;

    @Column(name = "weekly_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal weeklyMultiplier;

    @Column(name = "weekend_days", length = 30)
    private String weekendDays;

    @Column(name = "weekend_multiplier", precision = 5, scale = 2)
    private BigDecimal weekendMultiplier;

    @Column(name = "holiday_multiplier", precision = 5, scale = 2)
    private BigDecimal holidayMultiplier;

    @Column(name = "night_start")
    private LocalTime nightStart;

    @Column(name = "night_end")
    private LocalTime nightEnd;

    @Column(name = "night_multiplier", precision = 5, scale = 2)
    private BigDecimal nightMultiplier;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(length = 300)
    private String note;

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
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Integer getDailyThresholdMinutes() { return dailyThresholdMinutes; }
    public void setDailyThresholdMinutes(Integer v) { this.dailyThresholdMinutes = v; }
    public Integer getWeeklyThresholdMinutes() { return weeklyThresholdMinutes; }
    public void setWeeklyThresholdMinutes(Integer v) { this.weeklyThresholdMinutes = v; }
    public BigDecimal getDailyMultiplier() { return dailyMultiplier; }
    public void setDailyMultiplier(BigDecimal v) { this.dailyMultiplier = v; }
    public BigDecimal getWeeklyMultiplier() { return weeklyMultiplier; }
    public void setWeeklyMultiplier(BigDecimal v) { this.weeklyMultiplier = v; }
    public String getWeekendDays() { return weekendDays; }
    public void setWeekendDays(String v) { this.weekendDays = v; }
    public BigDecimal getWeekendMultiplier() { return weekendMultiplier; }
    public void setWeekendMultiplier(BigDecimal v) { this.weekendMultiplier = v; }
    public BigDecimal getHolidayMultiplier() { return holidayMultiplier; }
    public void setHolidayMultiplier(BigDecimal v) { this.holidayMultiplier = v; }
    public LocalTime getNightStart() { return nightStart; }
    public void setNightStart(LocalTime v) { this.nightStart = v; }
    public LocalTime getNightEnd() { return nightEnd; }
    public void setNightEnd(LocalTime v) { this.nightEnd = v; }
    public BigDecimal getNightMultiplier() { return nightMultiplier; }
    public void setNightMultiplier(BigDecimal v) { this.nightMultiplier = v; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean v) { this.requiresApproval = v; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
