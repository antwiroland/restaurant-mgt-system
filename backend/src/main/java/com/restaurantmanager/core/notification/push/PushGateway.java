package com.restaurantmanager.core.notification.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Delivers push messages. Returns the tokens the provider reports as no longer registered. */
public interface PushGateway {
    List<String> send(List<String> tokens, String title, String body, Map<String, String> data);

    /** Default: write messages to the log (no provider configured). */
    class Logging implements PushGateway {
        private static final Logger log = LoggerFactory.getLogger(Logging.class);

        @Override
        public List<String> send(List<String> tokens, String title, String body, Map<String, String> data) {
            log.info("Push (not delivered) to {} device(s): {} - {} {}", tokens.size(), title, body, data);
            return List.of();
        }
    }

    /** Expo push service (works for Expo Go and EAS builds; FCM/APNs are handled by Expo). */
    class Expo implements PushGateway {
        private static final Logger log = LoggerFactory.getLogger(Expo.class);
        private static final int BATCH = 100;

        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private final ObjectMapper mapper;
        private final URI endpoint;
        private final String accessToken;

        public Expo(ObjectMapper mapper, String endpoint, String accessToken) {
            this.mapper = mapper;
            this.endpoint = URI.create(endpoint);
            this.accessToken = accessToken;
        }

        @Override
        public List<String> send(List<String> tokens, String title, String body, Map<String, String> data) {
            List<String> stale = new ArrayList<>();
            for (int start = 0; start < tokens.size(); start += BATCH) {
                List<String> batch = tokens.subList(start, Math.min(tokens.size(), start + BATCH));
                List<Map<String, Object>> messages = new ArrayList<>();
                for (String token : batch) {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("to", token);
                    message.put("title", title);
                    message.put("body", body);
                    message.put("sound", "default");
                    message.put("data", data);
                    messages.add(message);
                }
                try {
                    HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(messages)));
                    if (accessToken != null && !accessToken.isBlank()) {
                        request.header("Authorization", "Bearer " + accessToken);
                    }
                    HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 300) {
                        log.warn("Expo push returned {}: {}", response.statusCode(), response.body());
                        continue;
                    }
                    JsonNode tickets = mapper.readTree(response.body()).path("data");
                    for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                        JsonNode ticket = tickets.get(i);
                        if ("error".equals(ticket.path("status").asText())
                                && "DeviceNotRegistered".equals(ticket.path("details").path("error").asText())) {
                            stale.add(batch.get(i));
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Expo push failed: {}", ex.getMessage());
                }
            }
            return stale;
        }
    }
}
