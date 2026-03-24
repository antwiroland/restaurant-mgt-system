package com.restaurantmanager.core.table.dto;

import java.util.UUID;

public record TableQrResponse(
        UUID tableId,
        String tableNumber,
        String qrToken,
        String qrUrl
) {
}
