package com.restaurantmanager.core.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPinRequest(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be 4 digits") String pin
) {
}
