package com.restaurantmanager.core.phase9.discount;

import java.math.BigDecimal;

public record DiscountResult(BigDecimal discountAmount, BigDecimal newTotal) {
}
