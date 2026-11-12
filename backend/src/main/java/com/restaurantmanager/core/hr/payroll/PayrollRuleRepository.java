package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayrollRuleRepository extends JpaRepository<PayrollRuleEntity, UUID> {
    List<PayrollRuleEntity> findAllByOrderByDisplayOrderAscCodeAscEffectiveFromDesc();
}
