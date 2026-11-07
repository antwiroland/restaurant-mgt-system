package com.restaurantmanager.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        @NotBlank @Size(min = 6) String password,
        /** Token from POST /auth/otp/verify proving the phone was verified (required unless disabled). */
        String verificationToken
) {
    public RegisterRequest(String name, String phone, String email, String password) {
        this(name, phone, email, password, null);
    }
}
