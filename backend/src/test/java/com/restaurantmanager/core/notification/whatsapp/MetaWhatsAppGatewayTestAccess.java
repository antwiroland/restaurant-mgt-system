package com.restaurantmanager.core.notification.whatsapp;

import com.restaurantmanager.core.notification.NotificationProps;
import org.springframework.web.client.RestClient;

public final class MetaWhatsAppGatewayTestAccess {
    private MetaWhatsAppGatewayTestAccess() {
    }

    public static WhatsAppGateway create(NotificationProps props, RestClient restClient) {
        return new MetaWhatsAppGateway(props, restClient);
    }
}
