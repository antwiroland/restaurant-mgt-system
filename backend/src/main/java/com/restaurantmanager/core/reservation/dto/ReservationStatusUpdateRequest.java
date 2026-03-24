package com.restaurantmanager.core.reservation.dto;

import com.restaurantmanager.core.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public record ReservationStatusUpdateRequest(@NotNull ReservationStatus status) {
}
