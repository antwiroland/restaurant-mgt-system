package com.restaurantmanager.core.phase9.reconciliation;

import java.math.BigDecimal;

public record ReconciliationEntry(BigDecimal sales, BigDecimal refunds, BigDecimal voids, BigDecimal discounts, String cashierId) {
}
