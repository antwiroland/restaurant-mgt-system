package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreateRequest(
        @NotNull OrderType type,
        UUID tableId,
        String tableToken,
        @NotEmpty List<@Valid OrderCreateItemRequest> items,
        String deliveryAddress,
        Instant pickupTime,
        Instant estimatedDeliveryTime,
        UUID groupSessionId,
        String notes
) {
}
