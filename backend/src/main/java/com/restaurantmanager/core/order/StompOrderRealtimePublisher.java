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
    public void publishOrderCreated(UUID orderId, OrderType type, OrderStatus status, BigDecimal total, String tableToken) {
        Map<String, Object> payload = Map.of(
                "event", "ORDER_CREATED",
                "orderId", orderId,
                "type", type.name(),
                "status", status.name(),
                "total", total,
                "createdAt", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/orders.new", payload);
        if (tableToken != null && !tableToken.isBlank()) {
            messagingTemplate.convertAndSend("/topic/public.tables." + tableToken + ".orders", payload);
        }
    }

    @Override
    public void publishOrderStatusChanged(UUID orderId, OrderStatus previous, OrderStatus current, String tableToken) {
        Map<String, Object> payload = Map.of(
                "event", "ORDER_STATUS_CHANGED",
                "orderId", orderId,
                "previousStatus", previous.name(),
                "newStatus", current.name(),
                "updatedAt", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/orders.status", payload);
        if (tableToken != null && !tableToken.isBlank()) {
            messagingTemplate.convertAndSend("/topic/public.tables." + tableToken + ".orders", payload);
        }
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
