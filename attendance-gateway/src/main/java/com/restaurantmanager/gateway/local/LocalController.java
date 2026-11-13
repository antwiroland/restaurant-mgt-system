package com.restaurantmanager.gateway.local;

import com.restaurantmanager.gateway.config.GatewayProperties;
import com.restaurantmanager.gateway.device.CapturedEvent;
import com.restaurantmanager.gateway.device.DeviceManager;
import com.restaurantmanager.gateway.store.EventStore;
import com.restaurantmanager.gateway.sync.SyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Local-only endpoints (the server listens on 127.0.0.1): a status page for whoever looks after the gateway, and an
 * optional simulator that injects scans as if a device had sent them, for testing without hardware.
 */
@RestController
@RequestMapping("/local")
public class LocalController {
    private final EventStore store;
    private final DeviceManager devices;
    private final SyncService sync;
    private final GatewayProperties properties;

    public LocalController(EventStore store, DeviceManager devices, SyncService sync, GatewayProperties properties) {
        this.store = store;
        this.devices = devices;
        this.sync = sync;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("gatewayId", properties.gatewayId());
        status.put("version", SyncService.VERSION);
        status.put("backend", properties.backendUrl());
        status.put("pendingEvents", store.pendingCount());
        status.put("oldestPending", store.oldestPending().orElse(null));
        status.put("lastSyncAt", sync.lastSyncAt());
        status.put("failedSyncAttempts", sync.failedAttempts());
        status.put("lastError", sync.lastError());
        status.put("devices", devices.health());
        status.put("simulator", properties.simulator().enabled());
        return status;
    }

    public record SimulatedScan(@NotBlank String deviceSerialNumber, @NotBlank String employeeDeviceId, String eventType,
                                String authenticationMethod, Instant at) {}

    /** Example: {"deviceSerialNumber":"DS-K1T341-0001","employeeDeviceId":"1001","eventType":"checkIn"} */
    @PostMapping("/simulate")
    public Map<String, Object> simulate(@Valid @RequestBody SimulatedScan scan) {
        if (!properties.simulator().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulator is off (gateway.simulator.enabled)");
        }
        String id = "sim-" + UUID.randomUUID();
        Instant at = scan.at() == null ? Instant.now() : scan.at();
        boolean added = devices.capture(new CapturedEvent(scan.deviceSerialNumber(), id, scan.employeeDeviceId(), scan.eventType(),
                scan.authenticationMethod() == null ? "FACE" : scan.authenticationMethod(), at,
                "{\"simulated\":true,\"attendanceStatus\":\"" + (scan.eventType() == null ? "" : scan.eventType()) + "\"}"));
        int sent = sync.syncNow();
        return Map.of("deviceEventId", id, "queued", added, "sentNow", sent, "pendingEvents", store.pendingCount());
    }
}
