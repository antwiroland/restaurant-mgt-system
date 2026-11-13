package com.restaurantmanager.core.order;

import com.restaurantmanager.core.common.AfterCommit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StompOrderRealtimePublisher implements OrderRealtimePublisher {
    /** Public pickup board: tells the screen to reload, with no order data (the board fetches what it may show). */
    static final String PICKUP_BOARD = "/topic/public.pickup-board";
    private static final Map<String, Object> PICKUP_BOARD_PING = Map.of("event", "PICKUP_BOARD_CHANGED");
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
        send("/topic/orders.new", payload);
        send(PICKUP_BOARD, PICKUP_BOARD_PING);
        if (tableToken != null && !tableToken.isBlank()) {
            send("/topic/public.tables." + tableToken + ".orders", payload);
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
        send("/topic/orders.status", payload);
        send(PICKUP_BOARD, PICKUP_BOARD_PING);
        // Per-order topic (order IDs are unguessable UUIDs) so customer apps only receive their own updates.
        send("/topic/orders." + orderId, payload);
        if (tableToken != null && !tableToken.isBlank()) {
            send("/topic/public.tables." + tableToken + ".orders", payload);
        }
    }

    @Override
    public void publishGroupCartUpdated(String sessionCode, UUID participantId, BigDecimal groupTotal) {
        // participantId is null when the whole session changed (cancelled or expired); Map.of rejects nulls.
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("event", "GROUP_CART_UPDATED");
        payload.put("sessionCode", sessionCode);
        payload.put("participantId", participantId);
        payload.put("groupTotal", groupTotal);
        payload.put("updatedAt", Instant.now().toString());
        send("/topic/group." + sessionCode, payload);
    }

    /** Sent after the surrounding transaction commits, so listeners that reload see the change. */
    private void send(String destination, Object payload) {
        AfterCommit.run(() -> messagingTemplate.convertAndSend(destination, payload));
    }
}
