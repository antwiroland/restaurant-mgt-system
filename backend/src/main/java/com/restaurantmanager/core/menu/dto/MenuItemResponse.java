package com.restaurantmanager.core.menu.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MenuItemResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        /** On the menu: switched on, and not sold out at every location. */
        boolean available,
        boolean active,
        /** Branches where an ingredient has run out (a null entry: the restaurant's own stock, when it has no branches). */
        List<UUID> soldOutBranchIds
) {
}
