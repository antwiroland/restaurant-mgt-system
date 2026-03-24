package com.restaurantmanager.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.restaurantmanager.core.common.OverrideActionType;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PinVerifyResponse(
        String overrideToken,
        long expiresIn,
        OverrideActionType actionType,
        Instant lockedUntil
) {
}
