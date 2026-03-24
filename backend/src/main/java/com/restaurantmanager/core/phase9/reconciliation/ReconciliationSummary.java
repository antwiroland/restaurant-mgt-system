package com.restaurantmanager.core.phase9.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ReconciliationSummary {
    private BigDecimal totalSales = BigDecimal.ZERO;
    private BigDecimal totalRefunds = BigDecimal.ZERO;
    private BigDecimal totalVoids = BigDecimal.ZERO;
    private BigDecimal totalDiscounts = BigDecimal.ZERO;
    private UUID signedBy;
    private Instant signedAt;

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public BigDecimal getTotalRefunds() {
        return totalRefunds;
    }

    public void setTotalRefunds(BigDecimal totalRefunds) {
        this.totalRefunds = totalRefunds;
    }

    public BigDecimal getTotalVoids() {
        return totalVoids;
    }

    public void setTotalVoids(BigDecimal totalVoids) {
        this.totalVoids = totalVoids;
    }

    public BigDecimal getTotalDiscounts() {
        return totalDiscounts;
    }

    public void setTotalDiscounts(BigDecimal totalDiscounts) {
        this.totalDiscounts = totalDiscounts;
    }

    public UUID getSignedBy() {
        return signedBy;
    }

    public void setSignedBy(UUID signedBy) {
        this.signedBy = signedBy;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }
}
