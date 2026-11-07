package com.restaurantmanager.core.workforce;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkScheduleRepository extends JpaRepository<WorkScheduleEntity, UUID> {
    @Query("""
            select s from WorkScheduleEntity s
            where s.startsAt < :to and s.endsAt > :from
              and (:userId is null or s.userId = :userId)
            order by s.startsAt asc
            """)
    List<WorkScheduleEntity> findOverlapping(@Param("from") Instant from, @Param("to") Instant to, @Param("userId") UUID userId);
}
