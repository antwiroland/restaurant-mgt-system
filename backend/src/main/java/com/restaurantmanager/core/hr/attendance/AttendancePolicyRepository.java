package com.restaurantmanager.core.hr.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicyEntity, UUID> {
    Optional<AttendancePolicyEntity> findByBranchId(UUID branchId);

    Optional<AttendancePolicyEntity> findFirstByBranchIdIsNull();
}
