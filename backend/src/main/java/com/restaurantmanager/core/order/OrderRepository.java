package com.restaurantmanager.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByPickupCode(String pickupCode);

    List<OrderEntity> findByTableIdOrderByCreatedAtAsc(UUID tableId);

    List<OrderEntity> findByTable_QrTokenOrderByCreatedAtAsc(String tableToken);
}
