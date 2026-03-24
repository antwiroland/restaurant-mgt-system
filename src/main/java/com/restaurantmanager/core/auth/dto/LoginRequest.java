package com.restaurantmanager.core.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String phone,
        @NotBlank String password
) {
}
