package com.restaurantmanager.core.hr.tips;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** How tips or service charge are shared at a branch (or everywhere), from a date. Never edited: a change is a new row. */
@Entity
@Table(name = "hr_tip_rules")
public class TipRuleEntity {
    @Id
    private UUID id;

    @Column(name = "branch_id") private UUID branchId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TipSource source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TipMethod method;
    @Column(name = "house_percent", nullable = false, precision = 5, scale = 2) private BigDecimal housePercent = BigDecimal.ZERO;
    @Column(nullable = false) private boolean taxable = true;
    @Column(name = "min_minutes", nullable = false) private int minMinutes;
    @Column(name = "position_points", columnDefinition = "TEXT") private String positionPoints;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(length = 300) private String note;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public TipSource getSource() { return source; }
    public void setSource(TipSource v) { this.source = v; }
    public TipMethod getMethod() { return method; }
    public void setMethod(TipMethod v) { this.method = v; }
    public BigDecimal getHousePercent() { return housePercent; }
    public void setHousePercent(BigDecimal v) { this.housePercent = v; }
    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean v) { this.taxable = v; }
    public int getMinMinutes() { return minMinutes; }
    public void setMinMinutes(int v) { this.minMinutes = v; }
    public String getPositionPoints() { return positionPoints; }
    public void setPositionPoints(String v) { this.positionPoints = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { this.effectiveFrom = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
