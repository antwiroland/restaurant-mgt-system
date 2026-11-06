package com.restaurantmanager.core.financial.controls.discount;

import java.math.BigDecimal;

public record DiscountResult(BigDecimal discountAmount, BigDecimal newTotal) {
}
