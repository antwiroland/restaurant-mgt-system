package com.restaurantmanager.core.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record GroupAddItemsRequest(
        @NotNull UUID participantId,
        @NotEmpty List<@Valid OrderCreateItemRequest> items
) {
}
