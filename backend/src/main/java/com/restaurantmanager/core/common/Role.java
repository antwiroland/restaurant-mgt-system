package com.restaurantmanager.core.common;

public enum Role {
    ADMIN,
    MANAGER,
    CASHIER,
    CUSTOMER,
    /** Delivery rider: sees and updates only deliveries assigned to them. */
    RIDER,
    /**
     * HR officer: works only in the HR module (every HR permission by default) and is refused everything else, such as
     * POS, orders and payments. Without a branch they cover every branch; with one, that branch plus any extra HR scope.
     */
    HR
}
