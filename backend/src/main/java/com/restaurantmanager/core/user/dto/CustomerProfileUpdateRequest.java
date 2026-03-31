package com.restaurantmanager.core.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerProfileUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 20) String phone
) {
}
