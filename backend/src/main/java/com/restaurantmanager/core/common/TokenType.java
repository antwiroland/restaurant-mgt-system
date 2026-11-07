package com.restaurantmanager.core.common;

public enum TokenType {
    ACCESS,
    REFRESH,
    OVERRIDE,
    /** Short-lived proof that a phone number was verified by OTP; not usable as a bearer token. */
    PHONE_VERIFICATION
}
