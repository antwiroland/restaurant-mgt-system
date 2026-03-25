package com.restaurantmanager.core.table;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTableEntity, UUID> {
    boolean existsByNumberIgnoreCase(String number);

    Optional<RestaurantTableEntity> findByQrToken(String qrToken);

    List<RestaurantTableEntity> findByBranch_IdOrderByNumberAsc(UUID branchId);
}
