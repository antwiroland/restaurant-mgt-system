package com.restaurantmanager.core.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 255) String addressLine,
        @Size(max = 120) String city,
        @Size(max = 255) String landmark,
        boolean isDefault
) {
}
