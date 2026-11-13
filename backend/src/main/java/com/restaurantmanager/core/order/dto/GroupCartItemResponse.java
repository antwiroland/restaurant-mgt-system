package com.restaurantmanager.core.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One line in a group cart. {@code price} is the unit price including the chosen modifiers; {@code modifiers} lists
 * them, with each entry's {@code id} being the modifier option id.
 */
public record GroupCartItemResponse(
        UUID itemId,
        UUID menuItemId,
        String name,
        BigDecimal price,
        int quantity,
        String notes,
        List<OrderItemModifierResponse> modifiers
) {
}
