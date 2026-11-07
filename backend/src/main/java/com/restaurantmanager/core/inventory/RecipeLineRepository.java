package com.restaurantmanager.core.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecipeLineRepository extends JpaRepository<RecipeLineEntity, UUID> {
    List<RecipeLineEntity> findByMenuItemIdIn(Collection<UUID> menuItemIds);

    List<RecipeLineEntity> findByMenuItemId(UUID menuItemId);

    @Query("select distinct r.menuItemId from RecipeLineEntity r where r.ingredient.id in :ingredientIds")
    List<UUID> menuItemsUsing(@Param("ingredientIds") Collection<UUID> ingredientIds);
}
