package com.restaurantmanager.core.table.dto;

import com.restaurantmanager.core.table.TableStatus;
import jakarta.validation.constraints.NotNull;

public record TableStatusUpdateRequest(@NotNull TableStatus status) {
}
