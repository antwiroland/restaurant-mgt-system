package com.restaurantmanager.core.notification.whatsapp;

public interface WhatsAppGateway {
    void send(String phone, String message);
}
