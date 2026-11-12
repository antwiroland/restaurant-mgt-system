package com.restaurantmanager.core.hr.recruitment;

import com.restaurantmanager.core.hr.employee.EmploymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A job opening at a branch. */
@Entity
@Table(name = "hr_vacancies")
public class VacancyEntity {
    @Id
    private UUID id;

    @Column(name = "vacancy_number", nullable = false, unique = true, length = 30) private String vacancyNumber;
    @Column(name = "branch_id", nullable = false) private UUID branchId;
    @Column(name = "position_id") private UUID positionId;
    @Column(nullable = false, length = 160) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String requirements;
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20) private EmploymentType employmentType;
    @Column(nullable = false) private int openings = 1;
    @Column(name = "hired_count", nullable = false) private int hiredCount;
    @Column(name = "salary_min", precision = 14, scale = 2) private BigDecimal salaryMin;
    @Column(name = "salary_max", precision = 14, scale = 2) private BigDecimal salaryMax;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private VacancyStatus status = VacancyStatus.DRAFT;
    @Column(name = "closing_date") private LocalDate closingDate;
    @Column(name = "hiring_manager_id") private UUID hiringManagerId;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "close_reason", length = 500) private String closeReason;
    @Column(name = "created_by") private UUID createdBy;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

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
    public String getVacancyNumber() { return vacancyNumber; }
    public void setVacancyNumber(String v) { this.vacancyNumber = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID v) { this.positionId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getRequirements() { return requirements; }
    public void setRequirements(String v) { this.requirements = v; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType v) { this.employmentType = v; }
    public int getOpenings() { return openings; }
    public void setOpenings(int v) { this.openings = v; }
    public int getHiredCount() { return hiredCount; }
    public void setHiredCount(int v) { this.hiredCount = v; }
    public BigDecimal getSalaryMin() { return salaryMin; }
    public void setSalaryMin(BigDecimal v) { this.salaryMin = v; }
    public BigDecimal getSalaryMax() { return salaryMax; }
    public void setSalaryMax(BigDecimal v) { this.salaryMax = v; }
    public VacancyStatus getStatus() { return status; }
    public void setStatus(VacancyStatus v) { this.status = v; }
    public LocalDate getClosingDate() { return closingDate; }
    public void setClosingDate(LocalDate v) { this.closingDate = v; }
    public UUID getHiringManagerId() { return hiringManagerId; }
    public void setHiringManagerId(UUID v) { this.hiringManagerId = v; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant v) { this.openedAt = v; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String v) { this.closeReason = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
