package com.restaurantmanager.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemModifierRepository extends JpaRepository<OrderItemModifierEntity, UUID> {
    List<OrderItemModifierEntity> findByOrderItem_IdOrderByCreatedAtAsc(UUID orderItemId);

    List<OrderItemModifierEntity> findByOrderItem_IdInOrderByCreatedAtAsc(Iterable<UUID> orderItemIds);
}
