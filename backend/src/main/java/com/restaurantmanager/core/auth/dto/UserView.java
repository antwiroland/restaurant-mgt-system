package com.restaurantmanager.core.auth.dto;

import java.util.UUID;

public record UserView(
        UUID id,
        String name,
        String role
) {
}
