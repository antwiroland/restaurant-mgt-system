package com.restaurantmanager.core.shift.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.UUID;

public record ShiftOpenRequest(
        @DecimalMin("0.00") BigDecimal openingCash,
        UUID branchId,
        String notes
) {
}
