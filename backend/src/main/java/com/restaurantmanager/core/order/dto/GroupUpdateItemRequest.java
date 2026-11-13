package com.restaurantmanager.core.order.dto;

import jakarta.validation.constraints.Min;

/** Changes the quantity and notes of a group cart line. To change modifiers, remove the line and add it again. */
public record GroupUpdateItemRequest(
        @Min(1) int quantity,
        String notes
) {
}
