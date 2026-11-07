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
              and (:userId is null or e.userId = :userId)
            order by e.clockInAt asc
            """)
    List<TimeEntryEntity> findInRange(@Param("from") Instant from, @Param("to") Instant to, @Param("userId") UUID userId);
}
