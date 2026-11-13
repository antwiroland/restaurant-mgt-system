package com.restaurantmanager.gateway.sync;

import com.restaurantmanager.gateway.config.GatewayProperties;
import com.restaurantmanager.gateway.device.DeviceManager;
import com.restaurantmanager.gateway.store.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends queued events to the backend and reports health. When the backend cannot be reached the events simply stay in
 * the queue and sending is retried with a growing pause (up to 5 minutes), so nothing is lost and the backend is not
 * flooded when the internet comes back.
 */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    static final int BATCH = 100;
    static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    public static final String VERSION = "1.0.0";

    private final EventStore store;
    private final BackendClient backend;
    private final DeviceManager devices;
    private final GatewayProperties properties;

    private volatile Instant nextAttemptAt = Instant.EPOCH;
    private volatile int failedAttempts;
    private volatile String lastError;
    private volatile Instant lastSyncAt;

    public SyncService(EventStore store, BackendClient backend, DeviceManager devices, GatewayProperties properties) {
        this.store = store;
        this.backend = backend;
        this.devices = devices;
        this.properties = properties;
    }

    /** Sends everything pending, batch after batch. Returns how many events the backend confirmed. */
    @Scheduled(initialDelay = 5_000, fixedDelay = 10_000)
    public int syncNow() {
        if (Instant.now().isBefore(nextAttemptAt)) {
            return 0;
        }
        int confirmed = 0;
        try {
            for (int round = 0; round < 50; round++) {
                List<EventStore.QueuedEvent> batch = store.pending(BATCH);
                if (batch.isEmpty()) {
                    break;
                }
                BackendClient.BatchResult result;
                try {
                    result = backend.sendEvents(new BackendClient.EventBatch(properties.gatewayId(), batch.stream()
                            .map(e -> new BackendClient.EventPayload(e.deviceSerialNumber(), e.deviceEventId(), e.employeeDeviceId(),
                                    e.eventType(), e.authenticationMethod(), e.deviceTimestamp(), e.gatewayReceivedAt(), e.rawEvent()))
                            .toList()));
                } catch (Exception failure) {
                    store.markAttemptFailed(batch.stream().map(EventStore.QueuedEvent::id).toList(), message(failure));
                    throw failure;
                }
                // The backend answers every event; each answered one is safely stored there (even if it needs review).
                for (BackendClient.EventResult r : result.results()) {
                    store.markSynced(r.deviceSerialNumber(), r.deviceEventId(), r.status());
                    confirmed++;
                }
                if (result.results().size() < batch.size()) {
                    break; // something was not answered: try again on the next run
                }
            }
            succeeded();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            failed(failure);
        }
        return confirmed;
    }

    /** Health report every minute; the answer carries the device list, which is applied (and cached for offline starts). */
    @Scheduled(initialDelay = 1_000, fixedDelay = 60_000)
    public void heartbeat() {
        try {
            BackendClient.GatewayConfig config = backend.heartbeat(new BackendClient.Heartbeat(properties.gatewayId(), VERSION,
                    store.pendingCount(), failedAttempts, lastError, lastSyncAt, devices.health()));
            if (config != null) {
                devices.apply(config);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            log.warn("Heartbeat failed: {}", message(failure));
            devices.applyCachedIfIdle();
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void housekeeping() {
        int removed = store.purgeSynced(Duration.ofDays(properties.keepSyncedDays()));
        if (removed > 0) {
            log.info("Removed {} sent events older than {} days", removed, properties.keepSyncedDays());
        }
    }

    private void succeeded() {
        if (failedAttempts > 0) {
            log.info("Connected to the backend again; queue is being sent");
        }
        failedAttempts = 0;
        lastError = null;
        lastSyncAt = Instant.now();
        nextAttemptAt = Instant.EPOCH;
    }

    private void failed(Exception failure) {
        failedAttempts++;
        lastError = message(failure);
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 10L * (1L << Math.min(failedAttempts - 1, 5)));
        nextAttemptAt = Instant.now().plusSeconds(seconds);
        if (failure instanceof BackendClient.KeyRejectedException) {
            log.error("{}. Events are kept; replace the key in the configuration and restart.", lastError);
        } else {
            log.warn("Could not send events ({}); {} waiting, retrying in {} s", lastError, store.pendingCount(), seconds);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public String lastError() {
        return lastError;
    }

    public Instant lastSyncAt() {
        return lastSyncAt;
    }
}
