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

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {
    List<StockMovementEntity> findByIngredientIdOrderByCreatedAtDesc(UUID ingredientId);

    List<StockMovementEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    List<StockMovementEntity> findByOrderId(UUID orderId);
}
