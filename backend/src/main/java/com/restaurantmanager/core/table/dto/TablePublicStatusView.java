package com.restaurantmanager.core.table.dto;

import com.restaurantmanager.core.table.TableStatus;

public record TablePublicStatusView(
        String number,
        int capacity,
        String zone,
        TableStatus status
) {
}
