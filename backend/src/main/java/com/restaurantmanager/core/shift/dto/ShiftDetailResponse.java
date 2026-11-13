package com.restaurantmanager.core.shift.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A shift with its drawer breakdown: expected cash = opening + cash sales - cash refunds + pay-ins - pay-outs.
 * For an open shift the figures are live; for a closed one, as at closing.
 */
public record ShiftDetailResponse(
        ShiftResponse shift,
        int cashSalesCount,
        BigDecimal cashSales,
        int cashRefundsCount,
        BigDecimal cashRefunds,
        BigDecimal payIns,
        BigDecimal payOuts,
        BigDecimal expectedCash,
        List<CashMovementResponse> movements
) {
}
