package com.restaurantmanager.core.financial.controls.reconciliation;

import java.math.BigDecimal;

public record ReconciliationEntry(BigDecimal sales, BigDecimal refunds, BigDecimal voids, BigDecimal discounts, String cashierId) {
}
