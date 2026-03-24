package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
        @NotNull OrderStatus status
) {
}
