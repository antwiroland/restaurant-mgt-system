package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;

import java.time.Instant;
import java.util.UUID;

public record OrderPublicStatusView(
        UUID orderId,
        String tableNumber,
        OrderType type,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
