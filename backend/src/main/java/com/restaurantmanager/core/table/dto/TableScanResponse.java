package com.restaurantmanager.core.table.dto;

import com.restaurantmanager.core.table.TableStatus;

import java.util.UUID;

public record TableScanResponse(
        UUID tableId,
        String tableNumber,
        TableStatus status
) {
}
