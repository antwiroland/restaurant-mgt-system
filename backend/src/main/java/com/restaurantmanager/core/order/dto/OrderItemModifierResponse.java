package com.restaurantmanager.core.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemModifierResponse(
        UUID id,
        String groupName,
        String optionName,
        BigDecimal priceDelta
) {
}
