package com.restaurantmanager.core.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Min(0) int displayOrder,
        boolean active
) {
}
