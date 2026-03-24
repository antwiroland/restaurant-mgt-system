package com.restaurantmanager.core.menu.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        int displayOrder,
        boolean active
) {
}
