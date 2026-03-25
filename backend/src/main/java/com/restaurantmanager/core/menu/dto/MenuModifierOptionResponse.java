package com.restaurantmanager.core.menu.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuModifierOptionResponse(
        UUID id,
        String name,
        BigDecimal priceDelta
) {
}
