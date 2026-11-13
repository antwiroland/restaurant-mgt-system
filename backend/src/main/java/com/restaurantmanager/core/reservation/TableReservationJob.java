package com.restaurantmanager.core.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Every minute, holds tables for upcoming reservations and hands back tables held by cancelled or no-show
 * reservations. The rules live in {@link ReservationTableHolds}.
 */
@Component
public class TableReservationJob {

    private final ReservationTableHolds tableHolds;

    public TableReservationJob(ReservationTableHolds tableHolds) {
        this.tableHolds = tableHolds;
    }

    @Scheduled(fixedDelay = 60_000)
    public void syncReservedTables() {
        tableHolds.sync(Instant.now());
    }
}
