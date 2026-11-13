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
     * Active, not yet seated reservations that should be holding their table now: the booked time is at or before
     * {@code holdFrom} (now + lead time) and the party is not yet a no-show (booked time at or after {@code noShowCutoff}).
     */
    @Query("""
            SELECT r FROM ReservationEntity r
            JOIN FETCH r.table
            WHERE r.status IN ('PENDING', 'CONFIRMED')
              AND r.seatedAt IS NULL
              AND r.reservedAt <= :holdFrom
              AND r.reservedAt >= :noShowCutoff
            """)
    List<ReservationEntity> findDueToHold(@Param("holdFrom") Instant holdFrom,
                                          @Param("noShowCutoff") Instant noShowCutoff);

    /** Reservations still holding a table although they were cancelled or the party did not turn up in time. */
    @Query("""
            SELECT r FROM ReservationEntity r
            JOIN FETCH r.table
            WHERE r.tableHeldAt IS NOT NULL
              AND r.seatedAt IS NULL
              AND (r.status = 'CANCELLED' OR r.reservedAt < :noShowCutoff)
            """)
    List<ReservationEntity> findHoldsToRelease(@Param("noShowCutoff") Instant noShowCutoff);

    /** Whether another active reservation is due to hold this table now (so releasing it would be wrong). */
    @Query("""
            SELECT COUNT(r) > 0 FROM ReservationEntity r
            WHERE r.table.id = :tableId
              AND r.id <> :excludeId
              AND r.status IN ('PENDING', 'CONFIRMED')
              AND r.seatedAt IS NULL
              AND r.reservedAt <= :holdFrom
              AND r.reservedAt >= :noShowCutoff
            """)
    boolean existsOtherDueToHold(@Param("tableId") UUID tableId,
                                 @Param("excludeId") UUID excludeId,
                                 @Param("holdFrom") Instant holdFrom,
                                 @Param("noShowCutoff") Instant noShowCutoff);

    /** Active reservations currently holding this table: the ones a party seated at it belongs to. */
    @Query("""
            SELECT r FROM ReservationEntity r
            WHERE r.table.id = :tableId
              AND r.tableHeldAt IS NOT NULL
              AND r.seatedAt IS NULL
              AND r.status IN ('PENDING', 'CONFIRMED')
            """)
    List<ReservationEntity> findActiveHoldsForTable(@Param("tableId") UUID tableId);

    /** Puts a free table on RESERVED. Never touches a table that is OCCUPIED (a guest is still using it) or CLOSED. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RestaurantTableEntity t
               set t.status = com.restaurantmanager.core.table.TableStatus.RESERVED
             where t.id = :tableId
               and t.status = com.restaurantmanager.core.table.TableStatus.AVAILABLE
            """)
    int holdTableIfAvailable(@Param("tableId") UUID tableId);

    /** Hands a RESERVED table back. A table staff have since seated someone at (OCCUPIED) is left alone. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RestaurantTableEntity t
               set t.status = com.restaurantmanager.core.table.TableStatus.AVAILABLE
             where t.id = :tableId
               and t.status = com.restaurantmanager.core.table.TableStatus.RESERVED
            """)
    int releaseTableIfReserved(@Param("tableId") UUID tableId);
}
