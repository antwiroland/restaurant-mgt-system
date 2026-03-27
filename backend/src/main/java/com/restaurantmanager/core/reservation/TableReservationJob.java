package com.restaurantmanager.core.reservation;

import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.table.TableRealtimePublisher;
import com.restaurantmanager.core.table.TableStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Every minute, marks tables as RESERVED when a confirmed/pending reservation
 * starts within the next 15 minutes (or is already past its start time but not yet expired).
 */
@Component
public class TableReservationJob {

    private static final Logger log = LoggerFactory.getLogger(TableReservationJob.class);
    private static final int LEAD_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableRealtimePublisher realtimePublisher;

    public TableReservationJob(ReservationRepository reservationRepository,
                               RestaurantTableRepository tableRepository,
                               TableRealtimePublisher realtimePublisher) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_SCAN}, allEntries = true)
    public void markUpcomingTablesReserved() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(LEAD_MINUTES, ChronoUnit.MINUTES);

        List<ReservationEntity> upcoming = reservationRepository.findUpcoming(now, windowEnd);
        if (upcoming.isEmpty()) {
            return;
        }

        for (ReservationEntity reservation : upcoming) {
            RestaurantTableEntity table = reservation.getTable();
            if (table.getStatus() != TableStatus.RESERVED) {
                table.setStatus(TableStatus.RESERVED);
                tableRepository.save(table);
                realtimePublisher.publishTableStatusChanged(table.getNumber(), TableStatus.RESERVED);
                log.info("Table {} marked RESERVED for reservation {} starting at {}",
                        table.getNumber(), reservation.getId(), reservation.getReservedAt());
            }
        }
    }
}
