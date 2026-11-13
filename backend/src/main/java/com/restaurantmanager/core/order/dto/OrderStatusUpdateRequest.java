package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * @param pickupCode the code the customer shows at the counter. Required to hand a READY pickup over (COMPLETED),
 *                   so nobody can collect an order just by reading its number off the public board. Admins and
 *                   managers may hand over without it (a customer who lost their code).
 */
public record OrderStatusUpdateRequest(
        @NotNull OrderStatus status,
        String pickupCode
) {
    public OrderStatusUpdateRequest(OrderStatus status) {
        this(status, null);
    }
}
