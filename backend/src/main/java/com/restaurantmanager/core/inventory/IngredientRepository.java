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

public interface IngredientRepository extends JpaRepository<IngredientEntity, UUID> {
    List<IngredientEntity> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IngredientEntity i where i.id in :ids order by i.id")
    List<IngredientEntity> lockAll(@Param("ids") Collection<UUID> ids);
}
