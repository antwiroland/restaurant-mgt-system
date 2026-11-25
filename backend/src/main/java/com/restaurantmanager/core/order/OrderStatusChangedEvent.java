package com.restaurantmanager.core.order;

import java.util.UUID;

/**
 * Published (inside the transaction) whenever an order's status changes, so other modules such as course dining can
 * follow the kitchen without the order module depending on them.
 */
public record OrderStatusChangedEvent(UUID orderId, OrderStatus previous, OrderStatus current) {
}
