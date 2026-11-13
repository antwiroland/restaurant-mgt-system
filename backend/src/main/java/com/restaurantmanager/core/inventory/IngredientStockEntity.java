package com.restaurantmanager.core.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stock of one ingredient at one location: a branch, or (branchId null) the restaurant when it has no branches. A
 * branch without a row for an ingredient does not track it.
 */
@Entity
@Table(name = "ingredient_stock")
public class IngredientStockEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private IngredientEntity ingredient;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "stock_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "low_stock_alerted", nullable = false)
    private boolean lowStockAlerted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = Instant.now();
    }

    /** At or below the ingredient's low-stock level. */
    public boolean isLow() {
        return stockQuantity.compareTo(ingredient.getLowStockThreshold()) <= 0;
    }

    public UUID getId() { return id; }
    public IngredientEntity getIngredient() { return ingredient; }
    public void setIngredient(IngredientEntity ingredient) { this.ingredient = ingredient; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public BigDecimal getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(BigDecimal stockQuantity) { this.stockQuantity = stockQuantity; }
    public boolean isLowStockAlerted() { return lowStockAlerted; }
    public void setLowStockAlerted(boolean lowStockAlerted) { this.lowStockAlerted = lowStockAlerted; }
    public Instant getUpdatedAt() { return updatedAt; }
}
