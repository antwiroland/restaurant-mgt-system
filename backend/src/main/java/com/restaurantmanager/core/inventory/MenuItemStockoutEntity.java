package com.restaurantmanager.core.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A dish that cannot be made at a location because an ingredient ran out there. */
@Entity
@Table(name = "menu_item_stockouts")
public class MenuItemStockoutEntity {
    @Id
    private UUID id;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    /** Null: the restaurant's own stock (no branches). */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMenuItemId() { return menuItemId; }
    public void setMenuItemId(UUID menuItemId) { this.menuItemId = menuItemId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public Instant getCreatedAt() { return createdAt; }
}
