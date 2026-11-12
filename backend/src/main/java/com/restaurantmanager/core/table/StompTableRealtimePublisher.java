package com.restaurantmanager.core.table;

import com.restaurantmanager.core.common.AfterCommit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class StompTableRealtimePublisher implements TableRealtimePublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public StompTableRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishTableStatusChanged(String tableNumber, TableStatus status) {
        send("/topic/tables.status", Map.of(
                "event", "TABLE_STATUS_CHANGED",
                "tableNumber", tableNumber,
                "status", status.name(),
                "updatedAt", Instant.now().toString()
        ));
    }

    /** Sent after the surrounding transaction commits, so listeners that reload see the change. */
    private void send(String destination, Object payload) {
        AfterCommit.run(() -> messagingTemplate.convertAndSend(destination, payload));
    }
}
