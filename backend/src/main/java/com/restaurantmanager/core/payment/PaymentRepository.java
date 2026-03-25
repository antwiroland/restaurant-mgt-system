package com.restaurantmanager.core.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByPaystackReference(String paystackReference);

    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<PaymentEntity> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    List<PaymentEntity> findByOrderIdInAndStatus(Iterable<UUID> orderIds, PaymentStatus status);

    List<PaymentEntity> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(Iterable<PaymentStatus> statuses, Instant createdBefore);

    List<PaymentEntity> findByMethodAndStatusAndPaidAtBetween(PaymentMethod method, PaymentStatus status, Instant from, Instant to);

    Optional<PaymentEntity> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, PaymentStatus status);
}
