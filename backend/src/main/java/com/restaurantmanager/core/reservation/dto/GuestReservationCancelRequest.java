package com.restaurantmanager.core.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestReservationCancelRequest(
        @NotBlank @Size(max = 20) String phone
) {
}
