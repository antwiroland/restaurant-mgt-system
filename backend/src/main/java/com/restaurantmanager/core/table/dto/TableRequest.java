package com.restaurantmanager.core.table.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TableRequest(
        @NotBlank @Size(max = 20) String number,
        @Min(1) int capacity,
        @Size(max = 120) String zone,
        UUID branchId
) {
}
