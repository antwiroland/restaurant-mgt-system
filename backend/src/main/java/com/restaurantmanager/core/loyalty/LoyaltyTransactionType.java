package com.restaurantmanager.core.loyalty;

public enum LoyaltyTransactionType {
    EARN,
    REDEEM,
    /** Signed correction: points restored after a cancelled order, or clawed back after a refund. */
    ADJUST
}
