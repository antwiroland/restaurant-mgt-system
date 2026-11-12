package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRunEntity, UUID> {
    @Query("select r from PayrollRunEntity r where r.branchId = :branchId and r.status <> :cancelled "
            + "and r.periodStart <= :to and r.periodEnd >= :from")
    List<PayrollRunEntity> findOverlapping(@Param("branchId") UUID branchId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                                           @Param("cancelled") PayrollRunStatus cancelled);

    List<PayrollRunEntity> findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(Collection<UUID> branchIds);

    List<PayrollRunEntity> findAllByOrderByPeriodStartDescCreatedAtDesc();

    List<PayrollRunEntity> findByIdInAndStatusIn(Collection<UUID> ids, Collection<PayrollRunStatus> statuses);
}
