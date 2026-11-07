package com.restaurantmanager.core.kds;

import java.util.List;

public record KdsOrderItem(
        java.util.UUID id,
        String name,
        int quantity,
        String notes,
        List<String> modifiers,
        boolean ready
) {
}
