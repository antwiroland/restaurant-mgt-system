package com.restaurantmanager.core.hr.contract;

/** DRAFT -> ACTIVE -> (EXPIRED | ENDED | RENEWED). Expiry never changes the employee's employment status. */
public enum ContractStatus {
    DRAFT,
    ACTIVE,
    EXPIRED,
    ENDED,
    RENEWED
}
