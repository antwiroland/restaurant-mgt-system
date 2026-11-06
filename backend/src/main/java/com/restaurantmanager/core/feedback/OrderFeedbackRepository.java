package com.restaurantmanager.core.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderFeedbackRepository extends JpaRepository<OrderFeedbackEntity, UUID> {
    Optional<OrderFeedbackEntity> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<OrderFeedbackEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from, Instant to);
}
