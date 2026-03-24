package com.restaurantmanager.core.phase10.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderAnalyticsRecord(UUID orderId,
                                   UUID customerId,
                                   UUID menuItemId,
                                   String itemName,
                                   int quantity,
                                   BigDecimal lineRevenue,
                                   int orderHour,
                                   Instant createdAt,
                                   boolean paymentSuccessful) {
}
