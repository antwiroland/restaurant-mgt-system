package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerUserId,
        OrderType type,
        OrderStatus status,
        UUID tableId,
        String tableNumber,
        String deliveryAddress,
        Instant pickupTime,
        String pickupCode,
        Instant estimatedDeliveryTime,
        UUID groupSessionId,
        String notes,
        String cancelReason,
        BigDecimal subtotal,
        BigDecimal total,
        Instant createdAt,
        List<OrderItemResponse> items
) {
}
