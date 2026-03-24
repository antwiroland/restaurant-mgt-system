package com.restaurantmanager.core.order.dto;

public record OrderCancelRequest(
        String reason,
        String overrideToken
) {
}
