package com.restaurantmanager.core.phase8.loyalty;

import java.time.Instant;

public record LoyaltyTransaction(int points, LoyaltyTransactionType type, String orderId, Instant createdAt) {
}
