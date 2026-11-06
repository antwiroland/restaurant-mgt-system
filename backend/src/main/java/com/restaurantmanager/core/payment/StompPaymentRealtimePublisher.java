package com.restaurantmanager.core.payment;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StompPaymentRealtimePublisher implements PaymentRealtimePublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public StompPaymentRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishPaymentStatusChanged(UUID paymentId, UUID orderId, PaymentStatus previous, PaymentStatus current,
                                            BigDecimal amount, PaymentMethod method) {
        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_STATUS_CHANGED",
                "paymentId", paymentId,
                "orderId", orderId,
                "previousStatus", previous.name(),
                "newStatus", current.name(),
                "amount", amount,
                "method", method.name(),
                "updatedAt", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/payments.status", payload);
        messagingTemplate.convertAndSend("/topic/orders." + orderId + ".payments", payload);
    }

    @Override
    public void publishPaymentFailed(UUID paymentId, UUID orderId, String reason) {
        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_FAILED",
                "paymentId", paymentId,
                "orderId", orderId,
                "reason", reason,
                "updatedAt", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/payments.failed", payload);
        messagingTemplate.convertAndSend("/topic/orders." + orderId + ".payments", payload);
    }
}
