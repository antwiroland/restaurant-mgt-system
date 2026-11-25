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
        List<KdsOrderItem> items,
        /** PICKUP orders are handed over at the counter with the customer's pickup code, not from the KDS. */
        com.restaurantmanager.core.order.OrderType orderType,
        /** The station preparing this ticket (e.g. KITCHEN, BAR); null: the main kitchen. */
        String kitchenStation,
        /** Course dining: the course this ticket belongs to, its meal, and who released it to the kitchen. */
        String courseName,
        UUID diningOrderId,
        UUID diningOrderCourseId,
        String releasedByName,
        java.time.Instant releasedAt
) {
}
