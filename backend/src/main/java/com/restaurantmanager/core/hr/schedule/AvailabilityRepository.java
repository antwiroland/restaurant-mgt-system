package com.restaurantmanager.core.hr.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<AvailabilityEntity, UUID> {
    List<AvailabilityEntity> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
