package com.restaurantmanager.core.notification.sms;

import com.restaurantmanager.core.notification.NotificationProps;
import com.restaurantmanager.core.notification.PhoneNumbers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Ghana SMS provider gateways.
 */
public final class HttpSmsGateways {
    private HttpSmsGateways() {
    }

    /** Hubtel Quick Send API (notifications.sms.provider=hubtel). */
    public static SmsGateway hubtel(NotificationProps props) {
        NotificationProps.Sms sms = props.getSms();
        requireValue(sms.getHubtelClientId(), "notifications.sms.hubtel-client-id");
        requireValue(sms.getHubtelClientSecret(), "notifications.sms.hubtel-client-secret");
        RestClient client = RestClient.create();
        return (phone, message) -> {
            String uri = UriComponentsBuilder.fromHttpUrl(sms.getHubtelBaseUrl())
                    .queryParam("clientid", sms.getHubtelClientId())
                    .queryParam("clientsecret", sms.getHubtelClientSecret())
                    .queryParam("from", sms.getSenderId())
                    .queryParam("to", PhoneNumbers.toInternational(phone, props.getDefaultCountryCode()))
                    .queryParam("content", message)
                    .encode()
                    .toUriString();
            client.get().uri(uri).retrieve().toBodilessEntity();
        };
    }

    /** Arkesel SMS v2 API (notifications.sms.provider=arkesel). */
    public static SmsGateway arkesel(NotificationProps props) {
        NotificationProps.Sms sms = props.getSms();
        requireValue(sms.getArkeselApiKey(), "notifications.sms.arkesel-api-key");
        RestClient client = RestClient.builder()
                .defaultHeader("api-key", sms.getArkeselApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return (phone, message) -> client.post()
                .uri(sms.getArkeselBaseUrl())
                .body(Map.of(
                        "sender", sms.getSenderId(),
                        "message", message,
                        "recipients", List.of(PhoneNumbers.toInternational(phone, props.getDefaultCountryCode()))
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private static void requireValue(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required for the configured SMS provider");
        }
    }
}
