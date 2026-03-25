package com.restaurantmanager.core.shift.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record ShiftCloseRequest(
        @DecimalMin("0.00") BigDecimal closingCash,
        String notes
) {
}
