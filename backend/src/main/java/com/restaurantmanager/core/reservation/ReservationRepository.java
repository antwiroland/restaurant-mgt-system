package com.restaurantmanager.core.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    List<ReservationEntity> findAllByCustomerUserIdOrderByReservedAtAsc(UUID customerUserId);

    /**
     * Find active reservations whose window starts within the next {@code windowEnd} instant
     * and has not yet expired (reservedAt + durationMins >= now).
     */
    @Query("""
            SELECT r FROM ReservationEntity r
            JOIN FETCH r.table
            WHERE r.status IN ('PENDING', 'CONFIRMED')
              AND r.reservedAt <= :windowEnd
              AND FUNCTION('TIMESTAMPADD', MINUTE, r.durationMins, r.reservedAt) >= :now
            """)
    List<ReservationEntity> findUpcoming(@Param("now") Instant now, @Param("windowEnd") Instant windowEnd);
}
