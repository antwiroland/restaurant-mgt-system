package com.restaurantmanager.core.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserView user
) {
}
