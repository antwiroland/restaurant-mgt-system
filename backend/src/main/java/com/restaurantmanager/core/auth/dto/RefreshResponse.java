package com.restaurantmanager.core.auth.dto;

public record RefreshResponse(
        String accessToken,
        long expiresIn
) {
}
