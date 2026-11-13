package com.restaurantmanager.gateway.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.restaurantmanager.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Calls the Restaurant Manager backend's gateway API with this gateway's key. */
@Component
public class BackendClient {

    /** The backend refused the key (revoked, rotated or wrong). Retrying will not help until the key is replaced. */
    public static class KeyRejectedException extends IOException {
        public KeyRejectedException(String message) {
            super(message);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceConfig(String serialNumber, String name, String vendor, String model, String ipAddress, Integer port,
                               boolean useHttps, String directionMode, boolean active) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GatewayConfig(String gatewayId, String name, int heartbeatSeconds, int syncSeconds, int recoveryMinutes,
                                List<DeviceConfig> devices) {}

    public record EventPayload(String deviceSerialNumber, String deviceEventId, String employeeDeviceId, String eventType,
                               String authenticationMethod, Instant deviceTimestamp, Instant gatewayReceivedAt, String rawEvent) {}

    public record EventBatch(String gatewayId, List<EventPayload> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventResult(String deviceSerialNumber, String deviceEventId, String status, String message, boolean duplicate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchResult(int received, List<EventResult> results) {}

    public record DeviceHealth(String serialNumber, String status, String message, Instant lastEventAt, Integer clockOffsetSeconds) {}

    public record Heartbeat(String gatewayId, String softwareVersion, int pendingEvents, int failedSyncAttempts, String lastError,
                            Instant lastSyncAt, List<DeviceHealth> devices) {}

    private final GatewayProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public BackendClient(GatewayProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public BatchResult sendEvents(EventBatch batch) throws IOException, InterruptedException {
        return call("POST", "/attendance/gateway/events", batch, BatchResult.class);
    }

    public GatewayConfig heartbeat(Heartbeat heartbeat) throws IOException, InterruptedException {
        return call("POST", "/attendance/gateway/heartbeat", heartbeat, GatewayConfig.class);
    }

    public GatewayConfig config() throws IOException, InterruptedException {
        return call("GET", "/attendance/gateway/config", null, GatewayConfig.class);
    }

    ObjectMapper mapper() {
        return mapper;
    }

    private <T> T call(String method, String path, Object body, Class<T> type) throws IOException, InterruptedException {
        if (properties.key() == null || properties.key().isBlank()) {
            throw new KeyRejectedException("No gateway key configured (gateway.key / GATEWAY_KEY)");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.backendUrl() + path))
                .timeout(Duration.ofSeconds(60))
                .header("X-Gateway-Key", properties.key())
                .header("Accept", "application/json");
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new KeyRejectedException("Backend refused the gateway key (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Backend answered HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
        }
        return response.body() == null || response.body().isBlank() ? null : mapper.readValue(response.body(), type);
    }

    private static String abbreviate(String text) {
        return text == null ? "" : text.length() > 200 ? text.substring(0, 200) : text;
    }
}
