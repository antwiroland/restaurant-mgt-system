package com.restaurantmanager.core.kds;

import com.restaurantmanager.core.order.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KdsOrderCard(
        UUID orderId,
        String tableNumber,
        String branchName,
        OrderStatus status,
        String notes,
        Instant createdAt,
        List<KdsOrderItem> items
) {
}
