package com.restaurantmanager.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(@NotBlank String phone, @NotBlank @Pattern(regexp = "[0-9]{4,8}") String code) {
}
