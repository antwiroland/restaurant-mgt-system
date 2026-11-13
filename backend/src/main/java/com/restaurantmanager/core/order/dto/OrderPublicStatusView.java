package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;

import java.time.Instant;

/**
 * One line on the public order board: the short order number, PICKUP or DELIVERY, and its status only. No items, table, customer or
 * full order id (the id unlocks per-order topics), so the board is safe to show on a screen anyone can see.
 */
public record OrderPublicStatusView(
        String orderNumber,
        OrderType type,
        OrderStatus status,
        Instant updatedAt
) {
}
