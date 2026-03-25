package com.restaurantmanager.core.kds;

import java.util.List;

public record KdsOrderItem(
        String name,
        int quantity,
        String notes,
        List<String> modifiers
) {
}
