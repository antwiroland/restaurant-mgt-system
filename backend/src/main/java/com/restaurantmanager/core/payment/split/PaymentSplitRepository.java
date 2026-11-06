package com.restaurantmanager.core.payment.split;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentSplitRepository extends JpaRepository<PaymentSplitEntity, UUID> {
    List<PaymentSplitEntity> findByOrderIdOrderByPositionAsc(UUID orderId);

    List<PaymentSplitEntity> findByOrderIdAndStatusNotOrderByPositionAsc(UUID orderId, PaymentSplitEntity.Status status);
}
