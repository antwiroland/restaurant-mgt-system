package com.restaurantmanager.core.loyalty;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyBalanceRepository extends JpaRepository<LoyaltyBalanceEntity, UUID> {
    Optional<LoyaltyBalanceEntity> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LoyaltyBalanceEntity b where b.customerId = :customerId")
    Optional<LoyaltyBalanceEntity> lockByCustomerId(@Param("customerId") UUID customerId);
}
