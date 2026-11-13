package com.restaurantmanager.core.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IngredientStockRepository extends JpaRepository<IngredientStockEntity, UUID> {
    /** Rows at one location (a null branchId means the restaurant's own stock), locked for an update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<IngredientStockEntity> findByIngredient_IdInAndBranchIdOrderByIngredient_IdAsc(Collection<UUID> ingredientIds, UUID branchId);

    /** Rows at one location, without locking. */
    List<IngredientStockEntity> findByBranchId(UUID branchId);

    List<IngredientStockEntity> findByIngredient_Id(UUID ingredientId);

    /** Every location that tracks any stock (null = the restaurant's own stock). */
    @Query("select distinct s.branchId from IngredientStockEntity s")
    List<UUID> trackedLocations();
}
