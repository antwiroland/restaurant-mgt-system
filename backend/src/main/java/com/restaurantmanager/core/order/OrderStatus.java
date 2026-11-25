package com.restaurantmanager.core.order;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    /**
     * Course dining: priced and on the table's bill, but held back from the kitchen until a waiter, cashier or manager
     * releases its course (it then becomes PENDING and shows on the KDS). Never shown on the KDS while held.
     */
    HELD
}
