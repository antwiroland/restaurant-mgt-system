package com.restaurantmanager.core.workforce;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkScheduleRepository extends JpaRepository<WorkScheduleEntity, UUID> {
    /**
     * Published shifts of staff with a login, overlapping [from, to). Used by the legacy rota, "my shifts", the time clock
     * and timesheets, so drafts, cancelled shifts and shifts of employees without a login never reach them.
     */
    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from
              and s.status = com.restaurantmanager.core.workforce.ScheduleStatus.PUBLISHED
              and s.userId is not null
              and (:userId is null or s.userId = :userId)
            order by s.startsAt asc
            """)
    List<WorkScheduleEntity> findOverlapping(@Param("from") Instant from, @Param("to") Instant to, @Param("userId") UUID userId);

    /** Draft or published shifts of one login overlapping [from, to); for overlap checks in the legacy rota. */
    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from and s.userId = :userId
              and s.status <> com.restaurantmanager.core.workforce.ScheduleStatus.CANCELLED
            """)
    List<WorkScheduleEntity> findActiveForUser(@Param("from") Instant from, @Param("to") Instant to, @Param("userId") UUID userId);

    /** Draft or published shifts of one employee overlapping [from, to). */
    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from and s.employeeId = :employeeId
              and s.status <> com.restaurantmanager.core.workforce.ScheduleStatus.CANCELLED
            order by s.startsAt asc
            """)
    List<WorkScheduleEntity> findActiveForEmployee(@Param("from") Instant from, @Param("to") Instant to, @Param("employeeId") UUID employeeId);

    /** All shifts (any status) at the given branches overlapping [from, to), for the HR schedule views. */
    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from and s.branchId in :branchIds
            order by s.startsAt asc
            """)
    List<WorkScheduleEntity> findAtBranches(@Param("from") Instant from, @Param("to") Instant to,
                                            @Param("branchIds") java.util.Collection<UUID> branchIds);

    /** Published shifts of one employee overlapping [from, to), for self-service. */
    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from and s.employeeId = :employeeId
              and s.status = com.restaurantmanager.core.workforce.ScheduleStatus.PUBLISHED
            order by s.startsAt asc
            """)
    List<WorkScheduleEntity> findPublishedForEmployee(@Param("from") Instant from, @Param("to") Instant to, @Param("employeeId") UUID employeeId);

    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from and s.branchId = :branchId
              and s.status = com.restaurantmanager.core.workforce.ScheduleStatus.DRAFT
            """)
    List<WorkScheduleEntity> findDraftsAtBranch(@Param("from") Instant from, @Param("to") Instant to, @Param("branchId") UUID branchId);

    /** Attaches an employee record to rota entries created for their login before the two were linked. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update WorkScheduleEntity s set s.employeeId = :employeeId where s.userId = :userId and s.employeeId is null")
    int attachEmployee(@Param("userId") UUID userId, @Param("employeeId") UUID employeeId);
}
