package com.restaurantmanager.core.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cancels pickup orders that were never paid, so they do not hold stock or clutter the POS. */
@Component
public class UnpaidPickupExpiryJob {
    private final OrderService orderService;
    private final int expireAfterMinutes;

    public UnpaidPickupExpiryJob(OrderService orderService,
                                 @Value("${orders.unpaid-pickup.expire-after-minutes:30}") int expireAfterMinutes) {
        this.orderService = orderService;
        this.expireAfterMinutes = expireAfterMinutes;
    }

    @Scheduled(fixedDelayString = "${orders.unpaid-pickup.check-every-ms:60000}")
    public void run() {
        orderService.expireUnpaidPickups(expireAfterMinutes);
    }
}
