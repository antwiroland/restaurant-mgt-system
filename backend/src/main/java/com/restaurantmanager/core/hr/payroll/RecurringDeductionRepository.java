package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringDeductionRepository extends JpaRepository<RecurringDeductionEntity, UUID> {
    List<RecurringDeductionEntity> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<RecurringDeductionEntity> findByEmployeeIdAndActiveTrueOrderByCreatedAtAsc(UUID employeeId);
}
