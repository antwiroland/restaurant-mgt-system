package com.restaurantmanager.gateway.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.gateway.config.GatewayProperties;
import com.restaurantmanager.gateway.config.SecretBox;
import com.restaurantmanager.gateway.hikvision.HikvisionIsapiConnector;
import com.restaurantmanager.gateway.store.EventStore;
import com.restaurantmanager.gateway.sync.BackendClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one connection per configured device: a listener thread for live scans (reconnecting with a growing pause), and
 * a periodic recovery search that fetches anything stored on the device since the last known scan, so events missed
 * while the gateway or network was down are not lost. Devices come from the backend; logins from local configuration.
 */
@Component
public class DeviceManager {
    private static final Logger log = LoggerFactory.getLogger(DeviceManager.class);
    private static final String CONFIG_SETTING = "last-config";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Builds the connector for a device. Replaceable in tests; new vendors are added here. */
    public interface ConnectorFactory {
        DeviceConnector create(BackendClient.DeviceConfig device, GatewayProperties.DeviceLogin login);
    }

    private final GatewayProperties properties;
    private final EventStore store;
    private final Map<String, Runner> runners = new ConcurrentHashMap<>();
    private volatile ConnectorFactory factory;
    private volatile boolean configApplied;

    public DeviceManager(GatewayProperties properties, EventStore store) {
        this.properties = properties;
        this.store = store;
        this.factory = this::defaultConnector;
    }

    public void setConnectorFactory(ConnectorFactory factory) {
        this.factory = factory;
    }

    private DeviceConnector defaultConnector(BackendClient.DeviceConfig device, GatewayProperties.DeviceLogin login) {
        if (!"HIKVISION".equalsIgnoreCase(device.vendor())) {
            throw new IllegalStateException("No connector for vendor " + device.vendor() + " yet");
        }
        String password = login.password();
        if (SecretBox.isEncrypted(password)) {
            password = SecretBox.fromEnvironment().decrypt(password);
        }
        return new HikvisionIsapiConnector(device.serialNumber(), device.ipAddress(),
                device.port() == null ? (device.useHttps() ? 443 : 80) : device.port(), device.useHttps(), login.username(), password,
                properties.acceptedMinorCodes(), ZoneId.of(properties.deviceTimeZone()));
    }

    // ------------------------------------------------------------------ configuration

    /** Applies the device list from the backend: starts new devices, stops removed or switched-off ones. */
    public synchronized void apply(BackendClient.GatewayConfig config) {
        configApplied = true;
        try {
            store.saveSetting(CONFIG_SETTING, MAPPER.writeValueAsString(config));
        } catch (Exception e) {
            log.warn("Could not cache the configuration: {}", e.getMessage());
        }
        List<BackendClient.DeviceConfig> wanted = config.devices() == null ? List.of() : config.devices().stream()
                .filter(BackendClient.DeviceConfig::active).toList();
        for (String serial : List.copyOf(runners.keySet())) {
            boolean keep = wanted.stream().anyMatch(d -> d.serialNumber().equalsIgnoreCase(serial) && sameTarget(d, runners.get(serial).device));
            if (!keep) {
                runners.remove(serial).stop();
                log.info("Stopped reading device {}", serial);
            }
        }
        for (BackendClient.DeviceConfig d : wanted) {
            runners.computeIfAbsent(d.serialNumber(), serial -> {
                Runner runner = new Runner(d);
                runner.start();
                return runner;
            });
        }
    }

    /** At start-up without internet: use the device list from the last successful contact. */
    public synchronized void applyCachedIfIdle() {
        if (configApplied) return;
        store.setting(CONFIG_SETTING).ifPresent(json -> {
            try {
                log.info("Backend unreachable; reading devices from the last saved configuration");
                apply(MAPPER.readValue(json, BackendClient.GatewayConfig.class));
            } catch (Exception e) {
                log.warn("Saved configuration unreadable: {}", e.getMessage());
            }
        });
    }

    private static boolean sameTarget(BackendClient.DeviceConfig a, BackendClient.DeviceConfig b) {
        return Objects.equals(a.ipAddress(), b.ipAddress()) && Objects.equals(a.port(), b.port()) && a.useHttps() == b.useHttps()
                && Objects.equals(a.vendor(), b.vendor());
    }

    // ------------------------------------------------------------------ recovery and health

    /** Every 15 minutes (and after each reconnect), fetch stored events the live stream may have missed. */
    @Scheduled(initialDelay = 120_000, fixedDelay = 900_000)
    public void recoverAll() {
        runners.values().forEach(Runner::recover);
    }

    public List<BackendClient.DeviceHealth> health() {
        List<BackendClient.DeviceHealth> out = new ArrayList<>();
        for (Runner r : runners.values()) {
            out.add(new BackendClient.DeviceHealth(r.device.serialNumber(), r.status, r.message,
                    store.lastEventAt(r.device.serialNumber()).orElse(null), r.clockOffsetSeconds));
        }
        return out;
    }

    public List<String> serials() {
        return List.copyOf(runners.keySet());
    }

    /** Stores a scan (used by listeners, recovery and the simulator). */
    public boolean capture(CapturedEvent event) {
        boolean added = store.add(event, Instant.now());
        if (added) {
            log.info("Scan {} from device {} user {} ({})", event.deviceEventId(), event.deviceSerialNumber(), event.employeeDeviceId(),
                    event.eventType() == null ? "no attendance status" : event.eventType());
        }
        return added;
    }

    @PreDestroy
    public void shutdown() {
        runners.values().forEach(Runner::stop);
        runners.clear();
    }

    /** One device: listener thread plus recovery. */
    private final class Runner {
        final BackendClient.DeviceConfig device;
        volatile String status = "UNKNOWN";
        volatile String message;
        volatile Integer clockOffsetSeconds;
        private volatile boolean running = true;
        private volatile DeviceConnector connector;
        private Thread thread;

        Runner(BackendClient.DeviceConfig device) {
            this.device = device;
        }

        void start() {
            thread = Thread.ofVirtual().name("device-" + device.serialNumber()).start(this::loop);
        }

        void stop() {
            running = false;
            DeviceConnector c = connector;
            if (c != null) c.close();
            if (thread != null) thread.interrupt();
        }

        private void loop() {
            GatewayProperties.DeviceLogin login = properties.loginFor(device.serialNumber());
            if (login == null) {
                status = "ERROR";
                message = "No login configured on the gateway for this device";
                log.error("Device {}: add its username/password under gateway.devices", device.serialNumber());
                return;
            }
            Duration pause = Duration.ofSeconds(5);
            while (running) {
                try {
                    connector = factory.create(device, login);
                    DeviceConnector.DeviceProbe probe = connector.probe();
                    clockOffsetSeconds = probe.clockOffsetSeconds();
                    status = "ONLINE";
                    message = probe.serialNumber() != null && !probe.serialNumber().contains(device.serialNumber())
                            && !device.serialNumber().contains(probe.serialNumber())
                            ? "Device reports serial " + probe.serialNumber() + "; check the registration" : null;
                    pause = Duration.ofSeconds(5);
                    recover();
                    connector.listen(DeviceManager.this::capture); // blocks while connected
                    if (running) throw new IllegalStateException("Event stream closed by the device");
                } catch (Exception failure) {
                    if (!running) break;
                    status = "OFFLINE";
                    message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                    log.warn("Device {} disconnected: {}; reconnecting in {} s", device.serialNumber(), message, pause.toSeconds());
                } finally {
                    DeviceConnector c = connector;
                    if (c != null) c.close();
                    connector = null;
                }
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException interrupted) {
                    break;
                }
                pause = pause.multipliedBy(2).compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : pause.multipliedBy(2);
            }
        }

        /** Fetches stored events from shortly before the last known one (or the initial look-back) until now. */
        synchronized void recover() {
            GatewayProperties.DeviceLogin login = properties.loginFor(device.serialNumber());
            if (login == null) return;
            Instant to = Instant.now();
            Instant from = store.cursor(device.serialNumber())
                    .map(last -> last.minus(Duration.ofMinutes(properties.recoveryOverlapMinutes())))
                    .orElse(to.minus(Duration.ofHours(properties.initialLookbackHours())));
            try (DeviceConnector search = factory.create(device, login)) {
                int added = 0;
                for (CapturedEvent e : search.search(from, to)) {
                    if (capture(e)) added++;
                }
                if (added > 0) {
                    log.info("Recovered {} missed scan(s) from device {}", added, device.serialNumber());
                }
            } catch (Exception failure) {
                log.warn("Recovery from device {} failed: {}", device.serialNumber(), failure.getMessage());
            }
        }
    }
}
