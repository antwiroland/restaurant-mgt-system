package com.restaurantmanager.core.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByPaystackReference(String paystackReference);

    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentEntity> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, PaymentStatus status);
}
