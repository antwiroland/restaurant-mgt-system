package com.restaurantmanager.core.common;

public enum Role {
    ADMIN,
    MANAGER,
    CASHIER,
    CUSTOMER,
    /** Delivery rider: sees and updates only deliveries assigned to them. */
    RIDER
}
