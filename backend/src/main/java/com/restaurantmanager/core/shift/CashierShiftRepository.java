package com.restaurantmanager.core.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashierShiftRepository extends JpaRepository<CashierShiftEntity, UUID> {
    Optional<CashierShiftEntity> findFirstByCashierIdAndStatusOrderByOpenedAtDesc(UUID cashierId, ShiftStatus status);

    List<CashierShiftEntity> findByStatusOrderByOpenedAtDesc(ShiftStatus status);

    List<CashierShiftEntity> findByStatusAndBranch_IdOrderByOpenedAtDesc(ShiftStatus status, UUID branchId);

    List<CashierShiftEntity> findByCashier_IdAndStatusOrderByOpenedAtDesc(UUID cashierId, ShiftStatus status);
}
