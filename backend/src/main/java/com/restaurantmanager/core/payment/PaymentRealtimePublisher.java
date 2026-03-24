package com.restaurantmanager.core.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentRealtimePublisher {
    void publishPaymentStatusChanged(UUID paymentId, UUID orderId, PaymentStatus previous, PaymentStatus current,
                                     BigDecimal amount, PaymentMethod method);

    void publishPaymentFailed(UUID paymentId, UUID orderId, String reason);
}
