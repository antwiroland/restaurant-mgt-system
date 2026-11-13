package com.restaurantmanager.core.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Ingredients a modifier option uses on top of the dish's recipe, per unit ordered (e.g. "Large": +0.1 kg rice). */
@Entity
@Table(name = "modifier_option_ingredients")
public class ModifierRecipeLineEntity {
    @Id
    private UUID id;

    @Column(name = "modifier_option_id", nullable = false)
    private UUID modifierOptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private IngredientEntity ingredient;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public UUID getModifierOptionId() { return modifierOptionId; }
    public void setModifierOptionId(UUID modifierOptionId) { this.modifierOptionId = modifierOptionId; }
    public IngredientEntity getIngredient() { return ingredient; }
    public void setIngredient(IngredientEntity ingredient) { this.ingredient = ingredient; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
