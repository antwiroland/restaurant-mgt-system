package com.restaurantmanager.core.order;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderRealtimePublisher {
    void publishOrderCreated(UUID orderId, OrderType type, OrderStatus status, BigDecimal total, String tableToken);

    void publishOrderStatusChanged(UUID orderId, OrderStatus previous, OrderStatus current, String tableToken);

    void publishGroupCartUpdated(String sessionCode, UUID participantId, BigDecimal groupTotal);
}
