package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustmentEntity, UUID> {
    List<PayrollAdjustmentEntity> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
