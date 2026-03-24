package com.restaurantmanager.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        @NotBlank @Size(min = 6) String password
) {
}
