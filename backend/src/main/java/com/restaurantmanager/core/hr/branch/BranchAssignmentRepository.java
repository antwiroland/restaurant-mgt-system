package com.restaurantmanager.core.hr.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchAssignmentRepository extends JpaRepository<BranchAssignmentEntity, UUID> {
    List<BranchAssignmentEntity> findByEmployeeIdOrderByStartDateDescCreatedAtDesc(UUID employeeId);

    Optional<BranchAssignmentEntity> findFirstByEmployeeIdAndAssignmentTypeAndEndDateIsNull(UUID employeeId, AssignmentType type);

    /**
     * Assignments of one employee overlapping [from, to] at a branch (to = null means open-ended). Two separate queries,
     * because PostgreSQL cannot infer the type of a null parameter in ":to is null".
     */
    default List<BranchAssignmentEntity> findOverlapping(UUID employeeId, UUID branchId, LocalDate from, LocalDate to) {
        return to == null ? findOverlappingOpenEnded(employeeId, branchId, from) : findOverlappingUntil(employeeId, branchId, from, to);
    }

    @Query("select a from BranchAssignmentEntity a where a.employeeId = :employeeId and a.branchId = :branchId "
            + "and (a.endDate is null or a.endDate >= :from)")
    List<BranchAssignmentEntity> findOverlappingOpenEnded(@Param("employeeId") UUID employeeId, @Param("branchId") UUID branchId,
                                                          @Param("from") LocalDate from);

    @Query("select a from BranchAssignmentEntity a where a.employeeId = :employeeId and a.branchId = :branchId "
            + "and (a.endDate is null or a.endDate >= :from) and a.startDate <= :to")
    List<BranchAssignmentEntity> findOverlappingUntil(@Param("employeeId") UUID employeeId, @Param("branchId") UUID branchId,
                                                      @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Branches where the employee holds a non-primary assignment active on {@code day}. */
    @Query("select a from BranchAssignmentEntity a where a.employeeId = :employeeId and a.assignmentType <> 'PRIMARY' "
            + "and a.startDate <= :day and (a.endDate is null or a.endDate >= :day)")
    List<BranchAssignmentEntity> findActiveSecondary(@Param("employeeId") UUID employeeId, @Param("day") LocalDate day);

    /** Number of distinct employees with an active non-primary assignment per branch on {@code day}. */
    @Query("select a.branchId, count(distinct a.employeeId) from BranchAssignmentEntity a where a.assignmentType <> 'PRIMARY' "
            + "and a.startDate <= :day and (a.endDate is null or a.endDate >= :day) group by a.branchId")
    List<Object[]> countActiveSecondaryByBranch(@Param("day") LocalDate day);

    /** Whether the employee belongs to the branch on {@code day} through any assignment (primary or not). */
    @Query("select count(a) > 0 from BranchAssignmentEntity a where a.employeeId = :employeeId and a.branchId = :branchId "
            + "and a.startDate <= :day and (a.endDate is null or a.endDate >= :day)")
    boolean existsActiveAt(@Param("employeeId") UUID employeeId, @Param("branchId") UUID branchId, @Param("day") LocalDate day);

    /** Employees with any assignment at the branch overlapping [from, to]. */
    @Query("select distinct a.employeeId from BranchAssignmentEntity a where a.branchId = :branchId "
            + "and a.startDate <= :to and (a.endDate is null or a.endDate >= :from)")
    List<UUID> findEmployeeIdsAtBranch(@Param("branchId") UUID branchId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
