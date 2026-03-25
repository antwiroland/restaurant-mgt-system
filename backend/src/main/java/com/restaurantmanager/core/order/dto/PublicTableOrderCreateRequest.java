package com.restaurantmanager.core.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PublicTableOrderCreateRequest(
        @NotBlank String tableToken,
        @NotEmpty List<@Valid OrderCreateItemRequest> items,
        String notes
) {
}
