package com.restaurantmanager.core.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal price,
        @Size(max = 1000) String imageUrl,
        boolean available
) {
}
