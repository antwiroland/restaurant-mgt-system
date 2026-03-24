package com.restaurantmanager.core.financial.dto;

import jakarta.validation.constraints.NotBlank;

public record OverrideTokenRequest(@NotBlank String overrideToken) {
}
