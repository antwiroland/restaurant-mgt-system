package com.restaurantmanager.core.table.dto;

import com.restaurantmanager.core.table.TableStatus;

import java.util.UUID;

public record TableResponse(
        UUID id,
        String number,
        int capacity,
        String zone,
        TableStatus status,
        String qrToken
) {
}
