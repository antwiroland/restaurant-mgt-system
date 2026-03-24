package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record GroupFinalizeRequest(
        @NotNull OrderType type,
        UUID tableId,
        String tableToken,
        String deliveryAddress,
        Instant pickupTime,
        Instant estimatedDeliveryTime,
        String notes
) {
}
