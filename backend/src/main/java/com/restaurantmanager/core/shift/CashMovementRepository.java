package com.restaurantmanager.core.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovementEntity, UUID> {
    List<CashMovementEntity> findByShiftIdOrderByCreatedAtAsc(UUID shiftId);
}
