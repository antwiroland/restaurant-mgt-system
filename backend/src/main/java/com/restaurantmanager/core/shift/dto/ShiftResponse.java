package com.restaurantmanager.core.shift.dto;

import com.restaurantmanager.core.shift.ShiftStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        UUID cashierUserId,
        String cashierName,
        UUID branchId,
        String branchName,
        ShiftStatus status,
        BigDecimal openingCash,
        BigDecimal closingCash,
        BigDecimal expectedCash,
        BigDecimal variance,
        String notes,
        Instant openedAt,
        Instant closedAt
) {
}
