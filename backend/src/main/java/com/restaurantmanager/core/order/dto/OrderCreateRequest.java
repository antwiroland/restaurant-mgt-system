package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
        String notes,
        String promoCode,
        @Min(0) Integer loyaltyPointsToRedeem
) {
    public OrderCreateRequest(OrderType type, UUID tableId, String tableToken, List<OrderCreateItemRequest> items,
                              String deliveryAddress, Instant pickupTime, Instant estimatedDeliveryTime,
                              UUID groupSessionId, String notes) {
        this(type, tableId, tableToken, items, deliveryAddress, pickupTime, estimatedDeliveryTime, groupSessionId, notes,
                null, null);
    }
}
