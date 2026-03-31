package com.restaurantmanager.core.phase8.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyBalanceRepository extends JpaRepository<LoyaltyBalanceEntity, UUID> {
    Optional<LoyaltyBalanceEntity> findByCustomerId(UUID customerId);
}
