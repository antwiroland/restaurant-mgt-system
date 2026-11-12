package com.restaurantmanager.core.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, UUID> {
    /** Requests of one employee in the given statuses overlapping [from, to] (inclusive dates). */
    @Query("select r from LeaveRequestEntity r where r.employeeId = :employeeId and r.status in :statuses "
            + "and r.startDate <= :to and r.endDate >= :from order by r.startDate")
    List<LeaveRequestEntity> findOverlapping(@Param("employeeId") UUID employeeId, @Param("statuses") Collection<LeaveStatus> statuses,
                                             @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Requests of the given employees in the given statuses overlapping [from, to]. */
    @Query("select r from LeaveRequestEntity r where r.employeeId in :employeeIds and r.status in :statuses "
            + "and r.startDate <= :to and r.endDate >= :from order by r.startDate")
    List<LeaveRequestEntity> findOverlappingForEmployees(@Param("employeeIds") Collection<UUID> employeeIds,
                                                         @Param("statuses") Collection<LeaveStatus> statuses,
                                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select r from LeaveRequestEntity r where r.branchId in :branchIds and r.status in :statuses "
            + "and r.startDate <= :to and r.endDate >= :from order by r.startDate")
    List<LeaveRequestEntity> findAtBranches(@Param("branchIds") Collection<UUID> branchIds, @Param("statuses") Collection<LeaveStatus> statuses,
                                            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select r from LeaveRequestEntity r where r.status in :statuses and r.startDate <= :to and r.endDate >= :from order by r.startDate")
    List<LeaveRequestEntity> findAllBranches(@Param("statuses") Collection<LeaveStatus> statuses,
                                             @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<LeaveRequestEntity> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    List<LeaveRequestEntity> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);
}
