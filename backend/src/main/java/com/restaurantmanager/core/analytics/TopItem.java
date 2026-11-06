package com.restaurantmanager.core.analytics;

import java.math.BigDecimal;

public record TopItem(String name, int quantitySold, BigDecimal revenue) {
}
