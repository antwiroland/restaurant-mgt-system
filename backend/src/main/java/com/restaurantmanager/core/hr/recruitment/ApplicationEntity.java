package com.restaurantmanager.core.hr.recruitment;

import com.restaurantmanager.core.hr.contract.ContractType;
import com.restaurantmanager.core.hr.contract.SalaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A candidate's application for one vacancy, with its pipeline stage and (at the offer stage) the offered terms. */
@Entity
@Table(name = "hr_applications", uniqueConstraints = @UniqueConstraint(columnNames = {"vacancy_id", "candidate_id"}))
public class ApplicationEntity {
    @Id
    private UUID id;

    @Column(name = "vacancy_id", nullable = false) private UUID vacancyId;
    @Column(name = "candidate_id", nullable = false) private UUID candidateId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private ApplicationStage stage = ApplicationStage.APPLIED;
    @Column(name = "reject_reason", length = 500) private String rejectReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "offer_contract_type", length = 20) private ContractType offerContractType;
    @Enumerated(EnumType.STRING)
    @Column(name = "offer_salary_type", length = 20) private SalaryType offerSalaryType;
    @Column(name = "offer_salary_amount", precision = 14, scale = 2) private BigDecimal offerSalaryAmount;
    @Column(name = "offer_weekly_hours", precision = 5, scale = 2) private BigDecimal offerWeeklyHours;
    @Column(name = "offer_start_date") private LocalDate offerStartDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "offer_status", length = 20) private OfferStatus offerStatus;
    @Column(name = "offer_sent_at") private Instant offerSentAt;
    @Column(name = "offer_responded_at") private Instant offerRespondedAt;
    @Column(name = "employee_id") private UUID employeeId;
    @Column(name = "hired_at") private Instant hiredAt;
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
    public UUID getVacancyId() { return vacancyId; }
    public void setVacancyId(UUID v) { this.vacancyId = v; }
    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID v) { this.candidateId = v; }
    public ApplicationStage getStage() { return stage; }
    public void setStage(ApplicationStage v) { this.stage = v; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String v) { this.rejectReason = v; }
    public ContractType getOfferContractType() { return offerContractType; }
    public void setOfferContractType(ContractType v) { this.offerContractType = v; }
    public SalaryType getOfferSalaryType() { return offerSalaryType; }
    public void setOfferSalaryType(SalaryType v) { this.offerSalaryType = v; }
    public BigDecimal getOfferSalaryAmount() { return offerSalaryAmount; }
    public void setOfferSalaryAmount(BigDecimal v) { this.offerSalaryAmount = v; }
    public BigDecimal getOfferWeeklyHours() { return offerWeeklyHours; }
    public void setOfferWeeklyHours(BigDecimal v) { this.offerWeeklyHours = v; }
    public LocalDate getOfferStartDate() { return offerStartDate; }
    public void setOfferStartDate(LocalDate v) { this.offerStartDate = v; }
    public OfferStatus getOfferStatus() { return offerStatus; }
    public void setOfferStatus(OfferStatus v) { this.offerStatus = v; }
    public Instant getOfferSentAt() { return offerSentAt; }
    public void setOfferSentAt(Instant v) { this.offerSentAt = v; }
    public Instant getOfferRespondedAt() { return offerRespondedAt; }
    public void setOfferRespondedAt(Instant v) { this.offerRespondedAt = v; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public Instant getHiredAt() { return hiredAt; }
    public void setHiredAt(Instant v) { this.hiredAt = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
