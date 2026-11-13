package com.restaurantmanager.core.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Cancels group order sessions left open with no activity, so abandoned carts do not stay OPEN forever. */
@Component
public class GroupSessionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(GroupSessionExpiryJob.class);

    private final OrderService orderService;
    private final long idleHours;

    public GroupSessionExpiryJob(OrderService orderService,
                                 @Value("${app.group-orders.idle-expiry-hours:24}") long idleHours) {
        this.orderService = orderService;
        this.idleHours = idleHours;
    }

    @Scheduled(fixedDelay = 15 * 60_000, initialDelay = 60_000)
    public void expireIdleSessions() {
        int expired = orderService.expireIdleGroupSessions(Instant.now().minus(idleHours, ChronoUnit.HOURS));
        if (expired > 0) {
            log.info("Cancelled {} group order session(s) idle for {}+ hours", expired, idleHours);
        }
    }
}
