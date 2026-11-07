package com.restaurantmanager.core.notification.email;

public interface EmailGateway {
    void send(String to, String subject, String textBody);
}
