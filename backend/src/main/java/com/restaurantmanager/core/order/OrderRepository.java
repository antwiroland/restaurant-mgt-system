package com.restaurantmanager.core.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByPickupCode(String pickupCode);

    List<OrderEntity> findByTableIdOrderByCreatedAtAsc(UUID tableId);

    List<OrderEntity> findByTable_QrTokenOrderByCreatedAtAsc(String tableToken);

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.table WHERE o.status NOT IN :excluded ORDER BY o.createdAt ASC")
    List<OrderEntity> findActiveForPublicDisplay(@Param("excluded") List<OrderStatus> excluded);
}
