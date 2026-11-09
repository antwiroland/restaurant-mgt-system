package com.restaurantmanager.core.workforce;

/** Lifecycle of a rota entry. Only PUBLISHED shifts are visible to staff and used by the time clock. */
public enum ScheduleStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
