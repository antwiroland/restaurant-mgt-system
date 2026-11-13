package com.restaurantmanager.core.hr.device;

import com.restaurantmanager.core.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * What a local attendance gateway may do with its key: send device events, report health, and read the devices it
 * should connect to. Keys look like {@code rmgw_<prefix>_<secret>}; only a SHA-256 hash is stored, and a revoked key
 * stops working at once.
 */
@Service
public class GatewayService {
    static final int MAX_BATCH = 200;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final AttendanceGatewayRepository gatewayRepository;
    private final AttendanceDeviceRepository deviceRepository;
    private final DeviceEventProcessor processor;

    public GatewayService(AttendanceGatewayRepository gatewayRepository, AttendanceDeviceRepository deviceRepository,
                          DeviceEventProcessor processor) {
        this.gatewayRepository = gatewayRepository;
        this.deviceRepository = deviceRepository;
        this.processor = processor;
    }

    // ------------------------------------------------------------------ keys

    public record IssuedKey(String key, String prefix, String hash) {}

    static IssuedKey newKey() {
        StringBuilder prefix = new StringBuilder("rmgw_");
        for (int i = 0; i < 10; i++) {
            prefix.append(PREFIX_ALPHABET.charAt(RANDOM.nextInt(PREFIX_ALPHABET.length())));
        }
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String key = prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedKey(key, prefix.toString(), hash(key));
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The gateway behind a key, or 401. */
    @Transactional(readOnly = true)
    public AttendanceGatewayEntity authenticate(String key) {
        if (key == null || !key.startsWith("rmgw_") || key.length() < 20) {
            throw new ApiException(401, "Gateway key missing or not valid");
        }
        String prefix = key.substring(0, Math.min(15, key.length()));
        AttendanceGatewayEntity gateway = gatewayRepository.findByKeyPrefix(prefix)
                .orElseThrow(() -> new ApiException(401, "Gateway key missing or not valid"));
        if (!MessageDigest.isEqual(gateway.getKeyHash().getBytes(StandardCharsets.UTF_8), hash(key).getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "Gateway key missing or not valid");
        }
        if (gateway.getRevokedAt() != null) {
            throw new ApiException(401, "This gateway key has been revoked");
        }
        return gateway;
    }

    // ------------------------------------------------------------------ events

    public record EventPayload(@NotBlank @Size(max = 80) String deviceSerialNumber,
                               @NotBlank @Size(max = 80) String deviceEventId,
                               @NotBlank @Size(max = 40) String employeeDeviceId,
                               @Size(max = 30) String eventType,
                               @Size(max = 20) String authenticationMethod,
                               @NotNull Instant deviceTimestamp,
                               Instant gatewayReceivedAt,
                               /** Anything else the device sent, kept as received. */
                               @Size(max = 8000) String rawEvent) {}

    public record EventBatch(@Size(max = 60) String gatewayId, @NotNull @Size(max = MAX_BATCH) List<@Valid EventPayload> events) {}

    public record EventResult(String deviceSerialNumber, String deviceEventId, DeviceEventEntity.Status status, String message,
                              boolean duplicate) {}

    public record BatchResult(int received, List<EventResult> results) {}

    /**
     * Stores and processes each event in device-time order (an employee's clock-in before their clock-out). Every event is
     * answered, so the gateway can drop it from its queue: the backend now holds it, even when it needs review.
     */
    public BatchResult receive(String key, EventBatch batch) {
        AttendanceGatewayEntity gateway = authenticate(key);
        checkIdentifier(gateway, batch.gatewayId());
        List<EventPayload> events = new ArrayList<>(batch.events());
        events.sort((a, b) -> a.deviceTimestamp().compareTo(b.deviceTimestamp()));
        List<EventResult> results = new ArrayList<>();
        for (EventPayload e : events) {
            var outcome = processor.process(gateway.getId(), new DeviceEventProcessor.IncomingEvent(e.deviceSerialNumber().trim(),
                    e.deviceEventId().trim(), e.employeeDeviceId().trim(), e.eventType(), e.authenticationMethod(), e.deviceTimestamp(),
                    e.gatewayReceivedAt(), e.rawEvent()));
            results.add(new EventResult(outcome.deviceSerialNumber(), outcome.deviceEventId(), outcome.status(), outcome.message(),
                    outcome.duplicate()));
        }
        markSynced(gateway.getId());
        return new BatchResult(results.size(), results);
    }

    // ------------------------------------------------------------------ health

    public record DeviceHealth(@NotBlank @Size(max = 80) String serialNumber, AttendanceDeviceEntity.ConnectionStatus status,
                               @Size(max = 300) String message, Instant lastEventAt, Integer clockOffsetSeconds) {}

    public record Heartbeat(@Size(max = 60) String gatewayId, @Size(max = 40) String softwareVersion, Integer pendingEvents,
                            Integer failedSyncAttempts, @Size(max = 500) String lastError, Instant lastSyncAt,
                            List<@Valid DeviceHealth> devices) {}

    public record SyncStatus(@Size(max = 60) String gatewayId, Integer pendingEvents, Integer failedSyncAttempts,
                             @Size(max = 500) String lastError, Instant lastSyncAt) {}

    @Transactional
    public GatewayConfig heartbeat(String key, Heartbeat beat) {
        AttendanceGatewayEntity gateway = authenticate(key);
        checkIdentifier(gateway, beat.gatewayId());
        Instant now = Instant.now();
        gateway.setLastHeartbeatAt(now);
        if (beat.softwareVersion() != null) gateway.setSoftwareVersion(beat.softwareVersion());
        applySync(gateway, beat.pendingEvents(), beat.failedSyncAttempts(), beat.lastError(), beat.lastSyncAt());
        gatewayRepository.save(gateway);
        if (beat.devices() != null) {
            for (DeviceHealth health : beat.devices()) {
                deviceRepository.findBySerialNumberIgnoreCase(health.serialNumber())
                        .filter(d -> gateway.getId().equals(d.getGatewayId()))
                        .ifPresent(d -> {
                            d.setConnectionStatus(health.status() == null ? AttendanceDeviceEntity.ConnectionStatus.UNKNOWN : health.status());
                            if (health.status() == AttendanceDeviceEntity.ConnectionStatus.ONLINE) d.setLastConnectedAt(now);
                            d.setStatusMessage(health.message());
                            d.setClockOffsetSeconds(health.clockOffsetSeconds());
                            if (health.lastEventAt() != null && (d.getLastEventAt() == null || health.lastEventAt().isAfter(d.getLastEventAt()))) {
                                d.setLastEventAt(health.lastEventAt().isAfter(now) ? now : health.lastEventAt());
                            }
                            deviceRepository.save(d);
                        });
            }
        }
        return config(gateway);
    }

    @Transactional
    public void syncStatus(String key, SyncStatus status) {
        AttendanceGatewayEntity gateway = authenticate(key);
        checkIdentifier(gateway, status.gatewayId());
        applySync(gateway, status.pendingEvents(), status.failedSyncAttempts(), status.lastError(), status.lastSyncAt());
        gatewayRepository.save(gateway);
    }

    // ------------------------------------------------------------------ configuration

    public record DeviceConfig(String serialNumber, String name, AttendanceDeviceEntity.Vendor vendor, String model,
                               String ipAddress, Integer port, boolean useHttps, AttendanceDeviceEntity.DirectionMode directionMode,
                               boolean active) {}

    public record GatewayConfig(String gatewayId, String name, int heartbeatSeconds, int syncSeconds, int recoveryMinutes,
                                List<DeviceConfig> devices) {}

    @Transactional(readOnly = true)
    public GatewayConfig config(String key) {
        return config(authenticate(key));
    }

    @Transactional(readOnly = true)
    public List<DeviceConfig> devices(String key) {
        return config(authenticate(key)).devices();
    }

    private GatewayConfig config(AttendanceGatewayEntity gateway) {
        List<DeviceConfig> devices = deviceRepository.findByGatewayIdOrderByNameAsc(gateway.getId()).stream()
                .map(d -> new DeviceConfig(d.getSerialNumber(), d.getName(), d.getVendor(), d.getModel(), d.getIpAddress(), d.getPort(),
                        d.isUseHttps(), d.getDirectionMode(), d.isActive()))
                .toList();
        return new GatewayConfig(gateway.getGatewayIdentifier(), gateway.getName(), 60, 10, 15, devices);
    }

    // ------------------------------------------------------------------ helpers

    private static void checkIdentifier(AttendanceGatewayEntity gateway, String claimed) {
        if (claimed != null && !claimed.isBlank() && !claimed.trim().equalsIgnoreCase(gateway.getGatewayIdentifier())) {
            throw new ApiException(403, "This key belongs to gateway " + gateway.getGatewayIdentifier());
        }
    }

    private static void applySync(AttendanceGatewayEntity gateway, Integer pending, Integer failed, String lastError, Instant lastSyncAt) {
        if (pending != null) gateway.setPendingEvents(Math.max(0, pending));
        if (failed != null) gateway.setFailedSyncAttempts(Math.max(0, failed));
        gateway.setLastError(lastError == null || lastError.isBlank() ? null : lastError.length() > 500 ? lastError.substring(0, 500) : lastError);
        if (lastSyncAt != null && (gateway.getLastSyncAt() == null || lastSyncAt.isAfter(gateway.getLastSyncAt()))) {
            gateway.setLastSyncAt(lastSyncAt.isAfter(Instant.now()) ? Instant.now() : lastSyncAt);
        }
    }

    @Transactional
    void markSynced(UUID gatewayId) {
        gatewayRepository.findById(gatewayId).ifPresent(g -> {
            g.setLastSyncAt(Instant.now());
            gatewayRepository.save(g);
        });
    }
}
