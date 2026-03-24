package com.restaurantmanager.core.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID menuItemId,
        UUID participantId,
        String name,
        BigDecimal price,
        int quantity,
        String notes
) {
}
