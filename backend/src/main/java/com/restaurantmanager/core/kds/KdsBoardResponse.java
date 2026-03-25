package com.restaurantmanager.core.kds;

import com.restaurantmanager.core.order.OrderStatus;

import java.util.List;
import java.util.Map;

public record KdsBoardResponse(
        Map<OrderStatus, List<KdsOrderCard>> columns
) {
}
