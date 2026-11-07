package com.restaurantmanager.core.auth.dto;

public record PhoneVerificationResponse(String phone, String verificationToken, long expiresIn) {
}
