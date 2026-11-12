package com.restaurantmanager.core.hr.tips;

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

/** Tips or service charge collected at one branch in a period, and how it was shared. */
@Entity
@Table(name = "hr_tip_pools")
public class TipPoolEntity {
    @Id
    private UUID id;

    @Column(name = "pool_number", nullable = false, unique = true, length = 30) private String poolNumber;
    @Column(name = "branch_id", nullable = false) private UUID branchId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TipSource source;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TipPoolStatus status = TipPoolStatus.DRAFT;
    @Column(name = "order_count", nullable = false) private int orderCount;
    @Column(name = "collected_amount", nullable = false, precision = 14, scale = 2) private BigDecimal collectedAmount = BigDecimal.ZERO;
    @Column(name = "house_amount", nullable = false, precision = 14, scale = 2) private BigDecimal houseAmount = BigDecimal.ZERO;
    @Column(name = "distributable_amount", nullable = false, precision = 14, scale = 2) private BigDecimal distributableAmount = BigDecimal.ZERO;
    @Column(name = "rule_snapshot", columnDefinition = "TEXT") private String ruleSnapshot;
    @Column(name = "calculated_by") private UUID calculatedBy;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
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
    public String getPoolNumber() { return poolNumber; }
    public void setPoolNumber(String v) { this.poolNumber = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public TipSource getSource() { return source; }
    public void setSource(TipSource v) { this.source = v; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate v) { this.periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate v) { this.periodEnd = v; }
    public TipPoolStatus getStatus() { return status; }
    public void setStatus(TipPoolStatus v) { this.status = v; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int v) { this.orderCount = v; }
    public BigDecimal getCollectedAmount() { return collectedAmount; }
    public void setCollectedAmount(BigDecimal v) { this.collectedAmount = v; }
    public BigDecimal getHouseAmount() { return houseAmount; }
    public void setHouseAmount(BigDecimal v) { this.houseAmount = v; }
    public BigDecimal getDistributableAmount() { return distributableAmount; }
    public void setDistributableAmount(BigDecimal v) { this.distributableAmount = v; }
    public String getRuleSnapshot() { return ruleSnapshot; }
    public void setRuleSnapshot(String v) { this.ruleSnapshot = v; }
    public UUID getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(UUID v) { this.calculatedBy = v; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant v) { this.calculatedAt = v; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String v) { this.cancelReason = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
