package com.restaurantmanager.core.hr.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OvertimeRuleRepository extends JpaRepository<OvertimeRuleEntity, UUID> {
    List<OvertimeRuleEntity> findByBranchIdOrderByEffectiveFromDesc(UUID branchId);

    List<OvertimeRuleEntity> findByBranchIdIsNullOrderByEffectiveFromDesc();
}
