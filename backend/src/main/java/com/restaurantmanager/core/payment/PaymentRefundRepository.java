package com.restaurantmanager.core.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefundEntity, UUID> {
    List<PaymentRefundEntity> findByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    List<PaymentRefundEntity> findByCreatedAtBetween(Instant from, Instant to);

    @org.springframework.data.jpa.repository.Query("""
            select r from PaymentRefundEntity r join fetch r.payment p join fetch p.order o left join fetch o.branch
            where r.createdAt >= :from and r.createdAt < :to and r.providerStatus <> 'failed'
            """)
    List<PaymentRefundEntity> findSuccessfulBetween(@org.springframework.data.repository.query.Param("from") Instant from,
                                                    @org.springframework.data.repository.query.Param("to") Instant to);
}
