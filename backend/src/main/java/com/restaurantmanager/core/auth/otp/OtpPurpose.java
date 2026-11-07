package com.restaurantmanager.core.auth.otp;

public enum OtpPurpose {
    /** Verify a new customer's phone before registration. */
    REGISTER,
    /** Passwordless login for an existing customer. */
    LOGIN
}
