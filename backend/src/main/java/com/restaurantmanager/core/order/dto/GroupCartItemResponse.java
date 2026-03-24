package com.restaurantmanager.core.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GroupCartItemResponse(
        UUID itemId,
        UUID menuItemId,
        String name,
        BigDecimal price,
        int quantity,
        String notes
) {
}
