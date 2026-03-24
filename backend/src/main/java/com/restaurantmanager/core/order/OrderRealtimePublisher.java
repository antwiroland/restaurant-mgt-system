package com.restaurantmanager.core.order;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderRealtimePublisher {
    void publishOrderCreated(UUID orderId, OrderType type, OrderStatus status, BigDecimal total);

    void publishOrderStatusChanged(UUID orderId, OrderStatus previous, OrderStatus current);

    void publishGroupCartUpdated(String sessionCode, UUID participantId, BigDecimal groupTotal);
}
