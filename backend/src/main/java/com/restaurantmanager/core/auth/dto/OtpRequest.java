package com.restaurantmanager.core.auth.dto;

import com.restaurantmanager.core.auth.otp.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OtpRequest(@NotBlank String phone, @NotNull OtpPurpose purpose) {
}
