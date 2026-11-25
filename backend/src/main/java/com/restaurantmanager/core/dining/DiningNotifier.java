package com.restaurantmanager.core.dining;

import com.restaurantmanager.core.common.AfterCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Live course dining events over the existing STOMP broker.
 *
 * <ul>
 *   <li>Staff of a branch: {@code /topic/dining.courses.{branchId}}; staff not tied to a branch (and admins):
 *   {@code /topic/dining.courses}. Waiters and cashiers get the same event, so whoever releases a course, the other
 *   sees it handled.</li>
 *   <li>The guest's table page: {@code /topic/public.tables.{qrToken}.courses} (no staff names).</li>
 * </ul>
 * Events are sent only after the transaction commits, and a failed send is logged and dropped: it never undoes an
 * order or a release. Screens reload their state from the API on every event and on reconnecting, so a missed event
 * is recovered by the next one or the next reconnect.
 */
@Component
public class DiningNotifier {
    static final String STAFF_TOPIC = "/topic/dining.courses";
    private static final Logger log = LoggerFactory.getLogger(DiningNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;

    public DiningNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Tells staff screens of the branch (and unscoped staff) that something changed; they reload. */
    public void staff(String event, UUID branchId, Map<String, Object> details) {
        Map<String, Object> payload = payload(event, details);
        send(STAFF_TOPIC, payload);
        if (branchId != null) {
            send(STAFF_TOPIC + "." + branchId, payload);
        }
    }

    /** Tells the guest's table page that its courses changed. */
    public void guest(String event, String tableToken, Map<String, Object> details) {
        if (tableToken == null || tableToken.isBlank()) {
            return;
        }
        send("/topic/public.tables." + tableToken + ".courses", payload(event, details));
    }

    private static Map<String, Object> payload(String event, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        details.forEach((key, value) -> {
            if (value != null) {
                payload.put(key, value instanceof UUID || value instanceof Enum<?> || value instanceof Instant ? value.toString() : value);
            }
        });
        payload.put("at", Instant.now().toString());
        return payload;
    }

    private void send(String destination, Object payload) {
        AfterCommit.run(() -> {
            try {
                messagingTemplate.convertAndSend(destination, payload);
            } catch (RuntimeException ex) {
                log.warn("Could not send course dining event to {}: {}", destination, ex.getMessage());
            }
        });
    }
}
