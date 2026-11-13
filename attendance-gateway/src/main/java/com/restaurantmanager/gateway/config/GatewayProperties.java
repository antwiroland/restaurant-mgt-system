package com.restaurantmanager.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/** Settings from application.yml / environment. Device logins live here only, never in the backend. */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        String backendUrl,
        String key,
        String gatewayId,
        String dataDir,
        String deviceTimeZone,
        Integer initialLookbackHours,
        Integer recoveryOverlapMinutes,
        Integer keepSyncedDays,
        Set<Integer> acceptedMinorCodes,
        List<DeviceLogin> devices,
        Simulator simulator
) {
    public record DeviceLogin(String serialNumber, String username, String password) {}

    public record Simulator(boolean enabled) {}

    public GatewayProperties {
        backendUrl = backendUrl == null ? "http://localhost:8080" : backendUrl.replaceAll("/+$", "");
        gatewayId = gatewayId == null || gatewayId.isBlank() ? "GATEWAY-001" : gatewayId;
        dataDir = dataDir == null ? "./gateway-data" : dataDir;
        deviceTimeZone = deviceTimeZone == null ? "Africa/Accra" : deviceTimeZone;
        initialLookbackHours = initialLookbackHours == null ? 24 : initialLookbackHours;
        recoveryOverlapMinutes = recoveryOverlapMinutes == null ? 10 : recoveryOverlapMinutes;
        keepSyncedDays = keepSyncedDays == null ? 30 : keepSyncedDays;
        acceptedMinorCodes = acceptedMinorCodes == null || acceptedMinorCodes.isEmpty() ? Set.of(1, 38, 75) : acceptedMinorCodes;
        devices = devices == null ? List.of() : devices;
        simulator = simulator == null ? new Simulator(false) : simulator;
    }

    public DeviceLogin loginFor(String serialNumber) {
        return devices.stream().filter(d -> d.serialNumber() != null && d.serialNumber().equalsIgnoreCase(serialNumber))
                .findFirst().orElse(null);
    }
}
