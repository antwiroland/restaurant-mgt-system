package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.table.TableStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record TableBillResponse(
        UUID tableId,
        String tableNumber,
        TableStatus tableStatus,
        int activeOrderCount,
        BigDecimal totalOrdered,
        BigDecimal totalPaid,
        BigDecimal outstandingTotal
) {
}
