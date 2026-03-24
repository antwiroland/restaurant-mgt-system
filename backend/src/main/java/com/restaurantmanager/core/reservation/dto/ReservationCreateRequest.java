package com.restaurantmanager.core.reservation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreateRequest(
        @NotNull UUID tableId,
        @Size(max = 120) String customerName,
        @Size(max = 20) String customerPhone,
        @Min(1) int partySize,
        @NotNull @Future Instant reservedAt,
        @Min(15) Integer durationMins,
        @Size(max = 1000) String notes
) {
}
