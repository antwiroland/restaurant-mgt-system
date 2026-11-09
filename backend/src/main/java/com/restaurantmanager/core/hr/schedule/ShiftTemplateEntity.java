package com.restaurantmanager.core.hr.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reusable shift pattern. Restaurant hours are never hard-coded: each template carries its own times and days. */
@Entity
@Table(name = "hr_shift_templates")
public class ShiftTemplateEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 20)
    private ShiftType shiftType;

    /** Null = usable at every branch. */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "days_of_week", nullable = false, length = 30)
    private String daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN";

    @Column(name = "min_staff", nullable = false)
    private int minStaff;

    @Column(name = "max_staff")
    private Integer maxStaff;

    @Column(nullable = false)
    private boolean active = true;

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

    public boolean crossesMidnight() {
        return !endTime.isAfter(startTime);
    }

    public Instant startOn(LocalDate date, ZoneId zone) {
        return date.atTime(startTime).atZone(zone).toInstant();
    }

    public Instant endOn(LocalDate date, ZoneId zone) {
        return (crossesMidnight() ? date.plusDays(1) : date).atTime(endTime).atZone(zone).toInstant();
    }

    public Set<DayOfWeek> days() {
        return Arrays.stream(daysOfWeek.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(ShiftTemplateEntity::parseDay).collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public void setDays(Set<DayOfWeek> days) {
        this.daysOfWeek = days.stream().sorted().map(ShiftTemplateEntity::formatDay).collect(Collectors.joining(","));
    }

    public static DayOfWeek parseDay(String code) {
        return Arrays.stream(DayOfWeek.values()).filter(d -> formatDay(d).equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown day " + code));
    }

    public static String formatDay(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ShiftType getShiftType() { return shiftType; }
    public void setShiftType(ShiftType shiftType) { this.shiftType = shiftType; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID positionId) { this.positionId = positionId; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public int getMinStaff() { return minStaff; }
    public void setMinStaff(int minStaff) { this.minStaff = minStaff; }
    public Integer getMaxStaff() { return maxStaff; }
    public void setMaxStaff(Integer maxStaff) { this.maxStaff = maxStaff; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
