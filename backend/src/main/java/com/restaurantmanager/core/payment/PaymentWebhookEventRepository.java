package com.restaurantmanager.core.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, String> {
}
