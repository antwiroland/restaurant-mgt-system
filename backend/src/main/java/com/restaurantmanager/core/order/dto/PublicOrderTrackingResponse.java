package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicOrderTrackingResponse(
        UUID orderId,
        String tableNumber,
        OrderStatus status,
        String notes,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal total,
        BigDecimal amountPaid
) {
}
