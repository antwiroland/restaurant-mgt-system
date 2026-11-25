package com.restaurantmanager.core.dining;

/**
 * Where one course of a meal is. PENDING and ON_HOLD courses are ordered but not yet released to the kitchen; from
 * RELEASED on, the status follows the course's kitchen orders.
 */
public enum CourseStatus {
    PENDING,
    ON_HOLD,
    RELEASED,
    PREPARING,
    READY,
    PARTIALLY_SERVED,
    SERVED,
    CANCELLED;

    public boolean awaitingRelease() {
        return this == PENDING || this == ON_HOLD;
    }
}
