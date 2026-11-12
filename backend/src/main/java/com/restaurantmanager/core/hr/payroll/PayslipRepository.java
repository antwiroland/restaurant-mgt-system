package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<PayslipEntity, UUID> {
    List<PayslipEntity> findByRunId(UUID runId);

    List<PayslipEntity> findByEmployeeId(UUID employeeId);

    /** Payslips of one employee in other non-cancelled runs overlapping [from, to]. */
    @Query("select p from PayslipEntity p, PayrollRunEntity r where p.runId = r.id and p.employeeId = :employeeId and r.id <> :runId "
            + "and r.status <> :cancelled and r.periodStart <= :to and r.periodEnd >= :from")
    List<PayslipEntity> findInOtherRuns(@Param("employeeId") UUID employeeId, @Param("runId") UUID runId, @Param("from") LocalDate from,
                                        @Param("to") LocalDate to, @Param("cancelled") PayrollRunStatus cancelled);

    @Modifying
    @Query("delete from PayslipEntity p where p.runId = :runId")
    void deleteByRunId(@Param("runId") UUID runId);
}
