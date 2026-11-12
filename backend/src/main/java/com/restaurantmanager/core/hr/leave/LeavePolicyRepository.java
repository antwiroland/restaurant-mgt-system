package com.restaurantmanager.core.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicyEntity, UUID> {
    List<LeavePolicyEntity> findByLeaveTypeIdOrderByEffectiveFromDesc(UUID leaveTypeId);

    List<LeavePolicyEntity> findAllByOrderByEffectiveFromDesc();
}
