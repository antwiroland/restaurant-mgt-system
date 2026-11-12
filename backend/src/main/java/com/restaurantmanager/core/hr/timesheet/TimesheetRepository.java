package com.restaurantmanager.core.hr.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<TimesheetEntity, UUID> {
    /** Timesheets of one employee overlapping [from, to]. */
    @Query("select t from TimesheetEntity t where t.employeeId = :employeeId and t.periodStart <= :to and t.periodEnd >= :from")
    List<TimesheetEntity> findOverlapping(@Param("employeeId") UUID employeeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select t from TimesheetEntity t where t.branchId in :branchIds and t.periodStart <= :to and t.periodEnd >= :from "
            + "order by t.periodStart desc, t.employeeId")
    List<TimesheetEntity> findAtBranches(@Param("branchIds") Collection<UUID> branchIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<TimesheetEntity> findByEmployeeIdOrderByPeriodStartDesc(UUID employeeId);

    List<TimesheetEntity> findByPayrollRunId(UUID payrollRunId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update TimesheetEntity t set t.payrollRunId = null where t.payrollRunId = :runId")
    void releaseRun(@Param("runId") UUID runId);
}
