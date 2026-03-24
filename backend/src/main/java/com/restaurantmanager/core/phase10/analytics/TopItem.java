package com.restaurantmanager.core.phase10.analytics;

import java.math.BigDecimal;

public record TopItem(String name, int quantitySold, BigDecimal revenue) {
}
