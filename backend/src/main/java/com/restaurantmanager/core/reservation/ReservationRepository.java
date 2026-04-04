package com.restaurantmanager.core.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    List<ReservationEntity> findAllByCustomerUserIdOrderByReservedAtAsc(UUID customerUserId);
    List<ReservationEntity> findAllByCustomerPhoneOrderByReservedAtAsc(String customerPhone);

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

    @Modifying
    @Query("""
            update RestaurantTableEntity t
               set t.status = 'RESERVED'
             where t.id = :tableId
               and t.status <> 'RESERVED'
               and exists (
                   select r.id from ReservationEntity r
                    where r.id = :reservationId
                      and r.table.id = :tableId
                      and r.status in ('PENDING', 'CONFIRMED')
                      and r.reservedAt <= :windowEnd
                      and FUNCTION('TIMESTAMPADD', MINUTE, r.durationMins, r.reservedAt) >= :now
               )
            """)
    int reserveTableIfReservationStillActive(@Param("reservationId") UUID reservationId,
                                             @Param("tableId") UUID tableId,
                                             @Param("now") Instant now,
                                             @Param("windowEnd") Instant windowEnd);
}
