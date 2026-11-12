package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;

import java.time.Instant;

/**
 * One line on the public pickup board: the short order number and its status only. No items, table, customer or
 * full order id (the id unlocks per-order topics), so the board is safe to show on a screen anyone can see.
 */
public record OrderPublicStatusView(
        String orderNumber,
        OrderStatus status,
        Instant updatedAt
) {
}
