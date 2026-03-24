package com.restaurantmanager.core.auth.dto;

import com.restaurantmanager.core.common.OverrideActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PinVerifyRequest(
        @NotBlank String pin,
        @NotNull OverrideActionType actionType
) {
}
