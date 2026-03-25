package com.restaurantmanager.core.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MenuModifierOptionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("-9999999999.99") BigDecimal priceDelta,
        @Min(0) int displayOrder,
        Boolean active
) {
}
