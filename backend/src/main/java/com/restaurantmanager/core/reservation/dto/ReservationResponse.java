package com.restaurantmanager.core.reservation.dto;

import com.restaurantmanager.core.reservation.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID tableId,
        String tableNumber,
        String customerName,
        String customerPhone,
        int partySize,
        Instant reservedAt,
        int durationMins,
        ReservationStatus status,
        String notes
) {
}
