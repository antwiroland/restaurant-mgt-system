package com.restaurantmanager.core.table.dto;

import jakarta.validation.constraints.NotBlank;

public record TableScanRequest(@NotBlank String qrToken) {
}
