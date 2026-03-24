package com.restaurantmanager.core.order;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StompOrderRealtimePublisher implements OrderRealtimePublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public StompOrderRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishOrderCreated(UUID orderId, OrderType type, OrderStatus status, BigDecimal total) {
        messagingTemplate.convertAndSend("/topic/orders.new", Map.of(
                "event", "ORDER_CREATED",
                "orderId", orderId,
                "type", type.name(),
                "status", status.name(),
                "total", total,
                "createdAt", Instant.now().toString()
        ));
    }

    @Override
    public void publishOrderStatusChanged(UUID orderId, OrderStatus previous, OrderStatus current) {
        messagingTemplate.convertAndSend("/topic/orders.status", Map.of(
                "event", "ORDER_STATUS_CHANGED",
                "orderId", orderId,
                "previousStatus", previous.name(),
                "newStatus", current.name(),
                "updatedAt", Instant.now().toString()
        ));
    }

    @Override
    public void publishGroupCartUpdated(String sessionCode, UUID participantId, BigDecimal groupTotal) {
        messagingTemplate.convertAndSend("/topic/group." + sessionCode, Map.of(
                "event", "GROUP_CART_UPDATED",
                "sessionCode", sessionCode,
                "participantId", participantId,
                "groupTotal", groupTotal,
                "updatedAt", Instant.now().toString()
        ));
    }
}
