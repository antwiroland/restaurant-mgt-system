package com.restaurantmanager.core.reservation;

import com.restaurantmanager.core.common.AfterCommit;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.table.TableRealtimePublisher;
import com.restaurantmanager.core.table.TableStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Keeps table status in step with reservations.
 * <ul>
 *   <li>Hold: from {@code hold-before-minutes} before the booked time a free table goes RESERVED, so staff can clean
 *       and set it. A table a guest is still using (OCCUPIED) is never interrupted; it is held once it is free.</li>
 *   <li>Seat: when the party orders at the held table (or staff mark it OCCUPIED) the reservation is seated and
 *       never holds the table again, so it goes back to AVAILABLE normally once the bill is paid.</li>
 *   <li>Release: a cancelled reservation, or a party that has not arrived {@code no-show-after-minutes} after the
 *       booked time, hands the table back to AVAILABLE unless another reservation is due to hold it.</li>
 * </ul>
 */
@Component
public class ReservationTableHolds {

    private static final Logger log = LoggerFactory.getLogger(ReservationTableHolds.class);

    private final ReservationRepository reservationRepository;
    private final TableRealtimePublisher realtimePublisher;
    private final CacheManager cacheManager;
    private final long holdBeforeMinutes;
    private final long noShowAfterMinutes;

    public ReservationTableHolds(ReservationRepository reservationRepository,
                                 TableRealtimePublisher realtimePublisher,
                                 CacheManager cacheManager,
                                 @Value("${app.reservations.hold-before-minutes:60}") long holdBeforeMinutes,
                                 @Value("${app.reservations.no-show-after-minutes:30}") long noShowAfterMinutes) {
        this.reservationRepository = reservationRepository;
        this.realtimePublisher = realtimePublisher;
        this.cacheManager = cacheManager;
        this.holdBeforeMinutes = holdBeforeMinutes;
        this.noShowAfterMinutes = noShowAfterMinutes;
    }

    /** Releases stale holds, then applies due ones. Run every minute by {@link TableReservationJob}. */
    @Transactional
    public void sync(Instant now) {
        for (ReservationEntity reservation : reservationRepository.findHoldsToRelease(noShowCutoff(now))) {
            if (reservation.getStatus() != ReservationStatus.CANCELLED) {
                log.info("Reservation {} for table {} is a no-show (booked {}); releasing the table",
                        reservation.getId(), reservation.getTable().getNumber(), reservation.getReservedAt());
            }
            release(reservation, now);
        }

        for (ReservationEntity reservation : reservationRepository.findDueToHold(holdFrom(now), noShowCutoff(now))) {
            if (reservation.getTableHeldAt() != null) {
                continue;
            }
            UUID tableId = reservation.getTable().getId();
            String tableNumber = reservation.getTable().getNumber();
            if (reservationRepository.holdTableIfAvailable(tableId) > 0) {
                reservation.setTableHeldAt(now);
                tableChanged(tableNumber, TableStatus.RESERVED);
                log.info("Table {} held (RESERVED) for reservation {} booked for {}",
                        tableNumber, reservation.getId(), reservation.getReservedAt());
            } else if (reservation.getTable().getStatus() == TableStatus.RESERVED) {
                // Already RESERVED (set by staff or a back-to-back booking): take the hold over so it is released later.
                reservation.setTableHeldAt(now);
            }
            // OCCUPIED or CLOSED: leave the guest be and try again next minute.
        }
    }

    /** Hands the reservation's table back if this reservation is holding it. Call when it is cancelled. */
    @Transactional
    public void release(ReservationEntity reservation, Instant now) {
        if (reservation.getTableHeldAt() == null) {
            return;
        }
        reservation.setTableHeldAt(null);
        reservationRepository.save(reservation);
        UUID tableId = reservation.getTable().getId();
        if (reservationRepository.existsOtherDueToHold(tableId, reservation.getId(), holdFrom(now), noShowCutoff(now))) {
            return;
        }
        if (reservationRepository.releaseTableIfReserved(tableId) > 0) {
            tableChanged(reservation.getTable().getNumber(), TableStatus.AVAILABLE);
            log.info("Table {} released from reservation {}", reservation.getTable().getNumber(), reservation.getId());
        }
    }

    /** The party for the reservation holding this table has been seated: the hold is used up. */
    @Transactional
    public void markSeated(UUID tableId, Instant now) {
        List<ReservationEntity> holds = reservationRepository.findActiveHoldsForTable(tableId);
        for (ReservationEntity reservation : holds) {
            reservation.setSeatedAt(now);
            reservation.setTableHeldAt(null);
        }
        reservationRepository.saveAll(holds);
    }

    private Instant holdFrom(Instant now) {
        return now.plus(holdBeforeMinutes, ChronoUnit.MINUTES);
    }

    private Instant noShowCutoff(Instant now) {
        return now.minus(noShowAfterMinutes, ChronoUnit.MINUTES);
    }

    private void tableChanged(String tableNumber, TableStatus status) {
        AfterCommit.run(() -> {
            for (String name : List.of(CacheConfig.TABLES, CacheConfig.TABLE_SCAN)) {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            }
            realtimePublisher.publishTableStatusChanged(tableNumber, status);
        });
    }
}
