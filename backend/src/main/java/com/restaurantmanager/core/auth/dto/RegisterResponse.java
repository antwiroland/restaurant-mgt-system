package com.restaurantmanager.core.auth.dto;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String name,
        String phone,
        String role,
        String accessToken,
        String refreshToken
) {
}
