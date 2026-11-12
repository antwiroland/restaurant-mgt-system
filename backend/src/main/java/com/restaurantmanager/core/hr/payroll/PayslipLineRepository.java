package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayslipLineRepository extends JpaRepository<PayslipLineEntity, UUID> {
    List<PayslipLineEntity> findByPayslipIdOrderBySortOrderAsc(UUID payslipId);

    @Modifying
    @Query("delete from PayslipLineEntity l where l.payslipId in (select p.id from PayslipEntity p where p.runId = :runId)")
    void deleteByRunId(@Param("runId") UUID runId);
}
