package com.restaurantmanager.core.notification.sms;

public interface SmsGateway {
    /**
     * Sends an SMS synchronously. Implementations throw a RuntimeException when delivery is rejected.
     */
    void send(String phone, String message);
}
