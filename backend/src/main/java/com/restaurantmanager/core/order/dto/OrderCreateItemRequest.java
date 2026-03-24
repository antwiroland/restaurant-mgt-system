package com.restaurantmanager.core.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderCreateItemRequest(
        @NotNull UUID menuItemId,
        @Min(1) int quantity,
        String notes
) {
}
