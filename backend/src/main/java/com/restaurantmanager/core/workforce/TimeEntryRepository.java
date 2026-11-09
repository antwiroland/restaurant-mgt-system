package com.restaurantmanager.core.workforce;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntryEntity, UUID> {
    Optional<TimeEntryEntity> findFirstByUserIdAndClockOutAtIsNull(UUID userId);

    @Query("""
            select e from TimeEntryEntity e
            where e.clockInAt >= :from and e.clockInAt < :to
              and e.userId is not null
              and (:userId is null or e.userId = :userId)
            order by e.clockInAt asc
            """)
    List<TimeEntryEntity> findInRange(@Param("from") Instant from, @Param("to") Instant to, @Param("userId") UUID userId);

    Optional<TimeEntryEntity> findFirstByEmployeeIdAndClockOutAtIsNull(UUID employeeId);

    /** Entries at the given branches that started in [from, to) or are still open, for HR attendance views. */
    @Query("""
            select e from TimeEntryEntity e
            where e.branchId in :branchIds
              and ((e.clockInAt >= :from and e.clockInAt < :to) or (e.clockOutAt is null and e.clockInAt < :to))
            order by e.clockInAt asc
            """)
    List<TimeEntryEntity> findAtBranches(@Param("from") Instant from, @Param("to") Instant to,
                                         @Param("branchIds") java.util.Collection<UUID> branchIds);

    @Query("""
            select e from TimeEntryEntity e
            where e.employeeId = :employeeId and e.clockInAt >= :from and e.clockInAt < :to
            order by e.clockInAt desc
            """)
    List<TimeEntryEntity> findForEmployee(@Param("employeeId") UUID employeeId, @Param("from") Instant from, @Param("to") Instant to);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update TimeEntryEntity e set e.employeeId = :employeeId where e.userId = :userId and e.employeeId is null")
    int attachEmployee(@Param("userId") UUID userId, @Param("employeeId") UUID employeeId);
}
