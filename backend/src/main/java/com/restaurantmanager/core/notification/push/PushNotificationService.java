package com.restaurantmanager.core.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Registers Expo push tokens and notifies customers about their orders and riders about new deliveries.
 * Messages are sent after the surrounding transaction commits, on the notification executor.
 * Enable with {@code PUSH_PROVIDER=expo}; otherwise messages are only logged.
 */
@Service
public class PushNotificationService {
    private final PushDeviceRepository repository;
    private final PushGateway gateway;
    private final ExecutorService executor;

    public PushNotificationService(PushDeviceRepository repository,
                                   ObjectMapper objectMapper,
                                   @Qualifier("notificationExecutor") ExecutorService executor,
                                   @Value("${app.push.provider:log}") String provider,
                                   @Value("${app.push.expo-endpoint:https://exp.host/--/api/v2/push/send}") String endpoint,
                                   @Value("${app.push.expo-access-token:}") String accessToken) {
        this.repository = repository;
        this.executor = executor;
        this.gateway = "expo".equalsIgnoreCase(provider)
                ? new PushGateway.Expo(objectMapper, endpoint, accessToken)
                : new PushGateway.Logging();
    }

    @Transactional
    public void register(UUID userId, DeviceRequest request) {
        PushDeviceEntity device = repository.findByToken(request.token().trim()).orElseGet(PushDeviceEntity::new);
        device.setToken(request.token().trim());
        device.setUserId(userId); // a phone that changes account follows the new user
        device.setPlatform(request.platform());
        device.setLastSeenAt(Instant.now());
        repository.save(device);
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        repository.findByToken(token).ifPresent(device -> {
            if (!device.getUserId().equals(userId)) {
                throw new ApiException(403, "Forbidden");
            }
            repository.delete(device);
        });
    }

    /** Customer-facing status messages. Silent for statuses the customer doesn't need to hear about. */
    public void orderStatusChanged(OrderEntity order, OrderStatus status) {
        if (order.getCustomerUserId() == null) {
            return;
        }
        String ref = "#" + order.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String[] message = switch (status) {
            case CONFIRMED -> new String[]{"Order confirmed", "We've got your order " + ref + " and the kitchen is on it."};
            case READY -> order.getType() == OrderType.DELIVERY
                    ? new String[]{"Order ready", "Your order " + ref + " is packed and waiting for a rider."}
                    : order.getType() == OrderType.PICKUP
                    ? new String[]{"Ready for pickup", "Your order " + ref + " is ready"
                        + (order.getPickupCode() == null ? "." : ". Pickup code " + order.getPickupCode() + ".")}
                    : new String[]{"Order ready", "Your order " + ref + " is on its way to your table."};
            case OUT_FOR_DELIVERY -> new String[]{"On the way", "Your order " + ref + " is out for delivery."};
            case DELIVERED -> new String[]{"Delivered", "Your order " + ref + " has been delivered. Enjoy!"};
            case CANCELLED -> new String[]{"Order cancelled", "Your order " + ref + " was cancelled."};
            default -> null;
        };
        if (message != null) {
            notifyUsers(List.of(order.getCustomerUserId()), message[0], message[1],
                    Map.of("type", "ORDER_STATUS", "orderId", order.getId().toString(), "status", status.name()));
        }
    }

    public void riderAssigned(UUID riderId, OrderEntity order) {
        String ref = "#" + order.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        notifyUsers(List.of(riderId), "New delivery", "Order " + ref + " is assigned to you"
                        + (order.getDeliveryAddress() == null ? "." : ": " + order.getDeliveryAddress()),
                Map.of("type", "DELIVERY_ASSIGNED", "orderId", order.getId().toString()));
    }

    public void notifyUsers(List<UUID> userIds, String title, String body, Map<String, String> data) {
        Runnable send = () -> executor.execute(() -> deliver(userIds, title, body, data));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void deliver(List<UUID> userIds, String title, String body, Map<String, String> data) {
        List<String> tokens = repository.findByUserIdIn(userIds).stream().map(PushDeviceEntity::getToken).toList();
        if (tokens.isEmpty()) {
            return;
        }
        List<String> stale = gateway.send(tokens, title, body, data);
        if (!stale.isEmpty()) {
            repository.deleteByTokenIn(stale);
        }
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 255)
            @Pattern(regexp = "^(ExponentPushToken|ExpoPushToken)\\[.+]$", message = "must be an Expo push token")
            String token,
            @Size(max = 20) String platform) {}
}
