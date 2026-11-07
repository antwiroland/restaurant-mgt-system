package com.restaurantmanager.core.financial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_signoffs")
public class ReconciliationSignOffEntity {
    @Id
    private UUID id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "gross_sales", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossSales;

    @Column(name = "total_refunds", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRefunds;

    @Column(name = "total_voids", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVoids;

    @Column(name = "total_discounts", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDiscounts;

    @Column(name = "net_sales", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSales;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(name = "cash_variance", precision = 12, scale = 2)
    private BigDecimal cashVariance;

    @Column(length = 1000)
    private String notes;

    @Column(name = "signed_by", nullable = false)
    private UUID signedBy;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (signedAt == null) {
            signedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public BigDecimal getGrossSales() { return grossSales; }
    public void setGrossSales(BigDecimal grossSales) { this.grossSales = grossSales; }
    public BigDecimal getTotalRefunds() { return totalRefunds; }
    public void setTotalRefunds(BigDecimal totalRefunds) { this.totalRefunds = totalRefunds; }
    public BigDecimal getTotalVoids() { return totalVoids; }
    public void setTotalVoids(BigDecimal totalVoids) { this.totalVoids = totalVoids; }
    public BigDecimal getTotalDiscounts() { return totalDiscounts; }
    public void setTotalDiscounts(BigDecimal totalDiscounts) { this.totalDiscounts = totalDiscounts; }
    public BigDecimal getNetSales() { return netSales; }
    public void setNetSales(BigDecimal netSales) { this.netSales = netSales; }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }
    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }
    public BigDecimal getCashVariance() { return cashVariance; }
    public void setCashVariance(BigDecimal cashVariance) { this.cashVariance = cashVariance; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getSignedBy() { return signedBy; }
    public void setSignedBy(UUID signedBy) { this.signedBy = signedBy; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
}
