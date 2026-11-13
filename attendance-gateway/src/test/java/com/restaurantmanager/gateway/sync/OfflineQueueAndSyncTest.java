package com.restaurantmanager.gateway.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.gateway.config.GatewayProperties;
import com.restaurantmanager.gateway.config.SecretBox;
import com.restaurantmanager.gateway.device.CapturedEvent;
import com.restaurantmanager.gateway.device.DeviceManager;
import com.restaurantmanager.gateway.store.EventStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Scans are kept while the backend is unreachable and sent, oldest first and once, when it comes back. */
class OfflineQueueAndSyncTest {
    @TempDir
    Path dir;

    private HttpServer backend;
    private volatile int backendStatus = 503;
    private final List<JsonNode> batches = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private EventStore store;
    private SyncService sync;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() throws IOException {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/attendance/gateway/events", ex -> {
            keys.add(ex.getRequestHeaders().getFirst("X-Gateway-Key"));
            JsonNode batch = new ObjectMapper().readTree(ex.getRequestBody());
            String body = "";
            if (backendStatus == 200) {
                batches.add(batch);
                List<String> results = new ArrayList<>();
                batch.path("events").forEach(e -> results.add("{\"deviceSerialNumber\":\"" + e.path("deviceSerialNumber").asText()
                        + "\",\"deviceEventId\":\"" + e.path("deviceEventId").asText() + "\",\"status\":\"ACCEPTED\",\"duplicate\":false}"));
                body = "{\"received\":" + results.size() + ",\"results\":[" + String.join(",", results) + "]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(backendStatus, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        });
        backend.start();

        dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + dir.resolve("gateway.db"), true);
        store = new EventStore(new JdbcTemplate(dataSource));
        store.createTables();
        GatewayProperties props = new GatewayProperties("http://127.0.0.1:" + backend.getAddress().getPort(), "rmgw_test_key_123456789",
                "GATEWAY-001", dir.toString(), "Africa/Accra", 24, 10, 30, Set.of(1, 38, 75), List.of(), null);
        sync = new SyncService(store, new BackendClient(props), new DeviceManager(props, store), props);
    }

    @AfterEach
    void tearDown() {
        backend.stop(0);
        dataSource.destroy();
    }

    private static CapturedEvent scan(String id, String type, String at) {
        return new CapturedEvent("DS-K1T-0001", id, "1001", type, "FACE", Instant.parse(at), "{}");
    }

    @Test
    void scansWaitInTheQueueWhileTheBackendIsDown_thenGoOutOldestFirst_once() throws Exception {
        assertThat(store.add(scan("2", "checkOut", "2026-11-13T17:00:00Z"), Instant.now())).isTrue();
        assertThat(store.add(scan("1", "checkIn", "2026-11-13T08:00:00Z"), Instant.now())).isTrue();
        // The same scan read again (live stream and recovery overlap) is stored once
        assertThat(store.add(scan("1", "checkIn", "2026-11-13T08:00:00Z"), Instant.now())).isFalse();
        assertThat(store.cursor("DS-K1T-0001")).contains(Instant.parse("2026-11-13T17:00:00Z"));

        // Internet down: nothing is lost
        assertThat(sync.syncNow()).isZero();
        assertThat(store.pendingCount()).isEqualTo(2);
        assertThat(sync.failedAttempts()).isEqualTo(1);
        assertThat(sync.lastError()).contains("503");
        // Waiting before the next attempt (backoff)
        backendStatus = 200;
        assertThat(sync.syncNow()).isZero();

        resetBackoff();
        assertThat(sync.syncNow()).isEqualTo(2);
        assertThat(store.pendingCount()).isZero();
        assertThat(batches).hasSize(1);
        JsonNode events = batches.get(0).path("events");
        assertThat(events.get(0).path("deviceEventId").asText()).isEqualTo("1");
        assertThat(events.get(0).path("deviceTimestamp").asText()).isEqualTo("2026-11-13T08:00:00Z");
        assertThat(events.get(1).path("eventType").asText()).isEqualTo("checkOut");
        assertThat(batches.get(0).path("gatewayId").asText()).isEqualTo("GATEWAY-001");
        assertThat(keys).allMatch("rmgw_test_key_123456789"::equals);

        // Nothing left to send
        assertThat(sync.syncNow()).isZero();
        assertThat(batches).hasSize(1);
    }

    @Test
    void aRejectedKey_keepsTheEvents() throws Exception {
        store.add(scan("9", "checkIn", "2026-11-13T08:00:00Z"), Instant.now());
        backendStatus = 401;
        sync.syncNow();
        assertThat(store.pendingCount()).isEqualTo(1);
        assertThat(sync.lastError()).contains("refused the gateway key");
    }

    @Test
    void devicePasswords_areEncryptedWithTheMasterKey() {
        SecretBox box = new SecretBox(Base64.getDecoder().decode(SecretBox.newMasterKey()));
        String sealed = box.encrypt("Passw0rd!");
        assertThat(sealed).startsWith("enc:").doesNotContain("Passw0rd!");
        assertThat(box.decrypt(sealed)).isEqualTo("Passw0rd!");
        assertThat(box.decrypt("plain")).isEqualTo("plain");
        SecretBox other = new SecretBox(Base64.getDecoder().decode(SecretBox.newMasterKey()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> other.decrypt(sealed)).hasMessageContaining("wrong GATEWAY_MASTER_KEY");
    }

    private void resetBackoff() throws Exception {
        Field next = SyncService.class.getDeclaredField("nextAttemptAt");
        next.setAccessible(true);
        next.set(sync, Instant.EPOCH);
    }
}
