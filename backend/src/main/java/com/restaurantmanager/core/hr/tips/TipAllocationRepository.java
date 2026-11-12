package com.restaurantmanager.core.hr.tips;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TipAllocationRepository extends JpaRepository<TipAllocationEntity, UUID> {
    List<TipAllocationEntity> findByPoolIdOrderByAmountDesc(UUID poolId);

    List<TipAllocationEntity> findByPoolIdIn(Collection<UUID> poolIds);

    List<TipAllocationEntity> findByEmployeeId(UUID employeeId);

    List<TipAllocationEntity> findByPayrollRunId(UUID payrollRunId);

    @Modifying
    @Query("delete from TipAllocationEntity a where a.poolId = :poolId")
    void deleteByPoolId(@Param("poolId") UUID poolId);

    @Modifying
    @Query("update TipAllocationEntity a set a.payrollRunId = null where a.payrollRunId = :runId")
    void releaseRun(@Param("runId") UUID runId);
}
