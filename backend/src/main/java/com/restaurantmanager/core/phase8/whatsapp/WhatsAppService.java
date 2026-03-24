package com.restaurantmanager.core.phase8.whatsapp;

import java.util.function.Consumer;

public class WhatsAppService {
    private final WhatsAppGateway gateway;
    private final Consumer<String> errorLogger;

    public WhatsAppService(WhatsAppGateway gateway, Consumer<String> errorLogger) {
        this.gateway = gateway;
        this.errorLogger = errorLogger;
    }

    public boolean sendOrderConfirmation(String phone, String orderId) {
        return safeSend(phone, "Order confirmed: " + orderId);
    }

    public boolean sendReceipt(String phone, String receiptLink, String reorderLink) {
        return safeSend(phone, "Receipt: " + receiptLink + " | Reorder: " + reorderLink);
    }

    public boolean sendStatusUpdate(String phone, String status) {
        return safeSend(phone, "Order status: " + status);
    }

    private boolean safeSend(String phone, String message) {
        try {
            gateway.send(phone, message);
            return true;
        } catch (RuntimeException ex) {
            errorLogger.accept("WhatsApp send failed: " + ex.getMessage());
            return false;
        }
    }
}
