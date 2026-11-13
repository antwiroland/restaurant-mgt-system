package com.restaurantmanager.core.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ModifierRecipeLineRepository extends JpaRepository<ModifierRecipeLineEntity, UUID> {
    List<ModifierRecipeLineEntity> findByModifierOptionId(UUID modifierOptionId);

    List<ModifierRecipeLineEntity> findByModifierOptionIdIn(Collection<UUID> modifierOptionIds);
}
