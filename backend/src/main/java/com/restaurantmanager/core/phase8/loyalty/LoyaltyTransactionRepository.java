package com.restaurantmanager.core.phase8.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransactionEntity, UUID> {
    List<LoyaltyTransactionEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
