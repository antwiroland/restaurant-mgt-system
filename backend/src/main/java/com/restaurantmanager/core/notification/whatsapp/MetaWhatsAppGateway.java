package com.restaurantmanager.core.notification.whatsapp;

import com.restaurantmanager.core.notification.NotificationProps;
import com.restaurantmanager.core.notification.PhoneNumbers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp Cloud API gateway (notifications.whatsapp.provider=meta).
 * Sends free-form text, or an approved template when notifications.whatsapp.template-name is set
 * (required for business-initiated messages outside the 24h customer-service window).
 */
public class MetaWhatsAppGateway implements WhatsAppGateway {
    private final RestClient restClient;
    private final NotificationProps.WhatsApp props;
    private final String defaultCountryCode;

    public MetaWhatsAppGateway(NotificationProps notificationProps) {
        this(notificationProps, RestClient.builder()
                .baseUrl(notificationProps.getWhatsapp().getApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + notificationProps.getWhatsapp().getAccessToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build());
    }

    MetaWhatsAppGateway(NotificationProps notificationProps, RestClient restClient) {
        this.props = notificationProps.getWhatsapp();
        this.defaultCountryCode = notificationProps.getDefaultCountryCode();
        this.restClient = restClient;
    }

    @Override
    public void send(String phone, String message) {
        restClient.post()
                .uri("/{version}/{phoneNumberId}/messages", props.getApiVersion(), props.getPhoneNumberId())
                .body(buildPayload(PhoneNumbers.toInternational(phone, defaultCountryCode), message))
                .retrieve()
                .toBodilessEntity();
    }

    Map<String, Object> buildPayload(String to, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        if (props.getTemplateName() != null && !props.getTemplateName().isBlank()) {
            payload.put("type", "template");
            payload.put("template", Map.of(
                    "name", props.getTemplateName(),
                    "language", Map.of("code", props.getTemplateLanguage()),
                    "components", List.of(Map.of(
                            "type", "body",
                            "parameters", List.of(Map.of("type", "text", "text", message))
                    ))
            ));
        } else {
            payload.put("type", "text");
            payload.put("text", Map.of("preview_url", true, "body", message));
        }
        return payload;
    }
}
