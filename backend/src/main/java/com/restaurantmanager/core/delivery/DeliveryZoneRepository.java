package com.restaurantmanager.core.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZoneEntity, UUID> {
    List<DeliveryZoneEntity> findByActiveTrueOrderByNameAsc();

    List<DeliveryZoneEntity> findAllByOrderByNameAsc();

    boolean existsByActiveTrue();
}
