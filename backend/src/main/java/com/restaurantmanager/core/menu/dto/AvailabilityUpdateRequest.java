package com.restaurantmanager.core.menu.dto;

import jakarta.validation.constraints.NotNull;

public record AvailabilityUpdateRequest(@NotNull Boolean available) {
}
