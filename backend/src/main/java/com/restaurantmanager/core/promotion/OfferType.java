package com.restaurantmanager.core.promotion;

/** The kinds of menu offer. */
public enum OfferType {
    /** Buy X of a dish, get Y more of the same dish free ("buy 2 get 1"). */
    BUY_X_GET_Y,
    /** Buy X of one dish, get Y of another dish free ("buy a burger, get a drink"). */
    BUY_X_GET_OTHER,
    /** A percentage off every unit of a dish. */
    PERCENT_OFF,
    /** Several dishes together for a fixed price. */
    COMBO
}
