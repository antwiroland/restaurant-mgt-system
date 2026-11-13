package com.restaurantmanager.gateway.store;

import com.restaurantmanager.gateway.device.CapturedEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The local queue (SQLite). Every scan is written here first, once per (device, device event id), and stays until the
 * backend has confirmed it; unsent events are never deleted. Also keeps each device's recovery cursor and the last
 * configuration received, so the gateway keeps reading devices while the internet is down, even after a restart.
 */
@Component
public class EventStore {
    public record QueuedEvent(String id, String deviceSerialNumber, String deviceEventId, String employeeDeviceId, String eventType,
                              String authenticationMethod, Instant deviceTimestamp, Instant gatewayReceivedAt, String rawEvent,
                              int attempts) {}

    private final JdbcTemplate jdbc;

    public EventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void createTables() {
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id TEXT PRIMARY KEY,
                    device_serial TEXT NOT NULL,
                    device_event_id TEXT NOT NULL,
                    employee_device_id TEXT NOT NULL,
                    event_type TEXT,
                    auth_method TEXT,
                    device_timestamp INTEGER NOT NULL,
                    gateway_received_at INTEGER NOT NULL,
                    raw_event TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    backend_status TEXT,
                    synced_at INTEGER,
                    UNIQUE (device_serial, device_event_id)
                )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_events_pending ON events(status, device_timestamp)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS cursors (device_serial TEXT PRIMARY KEY, last_event_at INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS settings (name TEXT PRIMARY KEY, value TEXT)");
    }

    /** Stores a scan unless it is already stored. Returns whether it was new. */
    public boolean add(CapturedEvent e, Instant receivedAt) {
        int inserted = jdbc.update("""
                        INSERT OR IGNORE INTO events (id, device_serial, device_event_id, employee_device_id, event_type, auth_method,
                                                      device_timestamp, gateway_received_at, raw_event)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID().toString(), e.deviceSerialNumber(), e.deviceEventId(), e.employeeDeviceId(), e.eventType(),
                e.authenticationMethod(), e.deviceTimestamp().toEpochMilli(), receivedAt.toEpochMilli(), e.rawEvent());
        if (inserted > 0) {
            advanceCursor(e.deviceSerialNumber(), e.deviceTimestamp());
        }
        return inserted > 0;
    }

    /** Oldest first (device time), so an employee's clock-in is sent before their clock-out. */
    public List<QueuedEvent> pending(int limit) {
        return jdbc.query("""
                        SELECT * FROM events WHERE status = 'PENDING' ORDER BY device_timestamp, device_event_id LIMIT ?""",
                (rs, i) -> new QueuedEvent(rs.getString("id"), rs.getString("device_serial"), rs.getString("device_event_id"),
                        rs.getString("employee_device_id"), rs.getString("event_type"), rs.getString("auth_method"),
                        Instant.ofEpochMilli(rs.getLong("device_timestamp")), Instant.ofEpochMilli(rs.getLong("gateway_received_at")),
                        rs.getString("raw_event"), rs.getInt("attempts")), limit);
    }

    /** The backend has the event (whatever it decided); it no longer needs sending. */
    public void markSynced(String deviceSerial, String deviceEventId, String backendStatus) {
        jdbc.update("""
                        UPDATE events SET status = 'SYNCED', backend_status = ?, synced_at = ?, last_error = NULL
                        WHERE device_serial = ? AND device_event_id = ?""",
                backendStatus, Instant.now().toEpochMilli(), deviceSerial, deviceEventId);
    }

    public void markAttemptFailed(List<String> ids, String error) {
        for (String id : ids) {
            jdbc.update("UPDATE events SET attempts = attempts + 1, last_error = ? WHERE id = ?", truncate(error), id);
        }
    }

    public int pendingCount() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE status = 'PENDING'", Integer.class);
        return n == null ? 0 : n;
    }

    public Optional<Instant> oldestPending() {
        Long at = jdbc.queryForObject("SELECT MIN(device_timestamp) FROM events WHERE status = 'PENDING'", Long.class);
        return Optional.ofNullable(at).map(Instant::ofEpochMilli);
    }

    /** Deletes sent events older than the retention period. Pending events are never deleted. */
    public int purgeSynced(Duration keep) {
        return jdbc.update("DELETE FROM events WHERE status = 'SYNCED' AND synced_at < ?", Instant.now().minus(keep).toEpochMilli());
    }

    public Optional<Instant> cursor(String deviceSerial) {
        List<Long> rows = jdbc.queryForList("SELECT last_event_at FROM cursors WHERE device_serial = ?", Long.class, deviceSerial);
        return rows.isEmpty() ? Optional.empty() : Optional.of(Instant.ofEpochMilli(rows.get(0)));
    }

    public Optional<Instant> lastEventAt(String deviceSerial) {
        return cursor(deviceSerial);
    }

    private void advanceCursor(String deviceSerial, Instant at) {
        jdbc.update("""
                        INSERT INTO cursors (device_serial, last_event_at) VALUES (?, ?)
                        ON CONFLICT(device_serial) DO UPDATE SET last_event_at = MAX(last_event_at, excluded.last_event_at)""",
                deviceSerial, at.toEpochMilli());
    }

    public void saveSetting(String name, String value) {
        jdbc.update("INSERT INTO settings (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value", name, value);
    }

    public Optional<String> setting(String name) {
        List<String> rows = jdbc.queryForList("SELECT value FROM settings WHERE name = ?", String.class, name);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
