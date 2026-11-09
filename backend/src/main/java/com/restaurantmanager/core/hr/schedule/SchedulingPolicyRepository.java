package com.restaurantmanager.core.hr.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SchedulingPolicyRepository extends JpaRepository<SchedulingPolicyEntity, UUID> {
    Optional<SchedulingPolicyEntity> findByBranchId(UUID branchId);

    Optional<SchedulingPolicyEntity> findFirstByBranchIdIsNull();
}
