package com.restaurantmanager.core.hr.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A biometric attendance terminal. Its login (username and password) is never stored here: only the gateway on the
 * restaurant network holds it, encrypted in its own configuration.
 */
@Entity
@Table(name = "hr_attendance_devices")
public class AttendanceDeviceEntity {
    public enum Vendor { HIKVISION, ZKTECO, OTHER }

    /**
     * How a scan becomes a clock action when the device does not report one. DEVICE_REPORTED: trust only what the device
     * says (no fallback, so anything unclear goes to review). The others fix one action for this terminal, e.g. an
     * "entrance" reader that only clocks people in.
     */
    public enum DirectionMode { DEVICE_REPORTED, CLOCK_IN, CLOCK_OUT, BREAK_OUT, BREAK_IN }

    public enum ConnectionStatus { UNKNOWN, ONLINE, OFFLINE, ERROR }

    @Id
    private UUID id;

    @Column(name = "gateway_id")
    private UUID gatewayId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Vendor vendor;

    @Column(length = 80)
    private String model;

    @Column(name = "serial_number", nullable = false, unique = true, length = 80)
    private String serialNumber;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    private Integer port;

    @Column(name = "use_https", nullable = false)
    private boolean useHttps;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction_mode", nullable = false, length = 20)
    private DirectionMode directionMode;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20)
    private ConnectionStatus connectionStatus = ConnectionStatus.UNKNOWN;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    /** Device clock minus gateway clock, as last measured by the gateway. */
    @Column(name = "clock_offset_seconds")
    private Integer clockOffsetSeconds;

    @Column(name = "status_message", length = 300)
    private String statusMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getGatewayId() { return gatewayId; }
    public void setGatewayId(UUID gatewayId) { this.gatewayId = gatewayId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public boolean isUseHttps() { return useHttps; }
    public void setUseHttps(boolean useHttps) { this.useHttps = useHttps; }
    public DirectionMode getDirectionMode() { return directionMode; }
    public void setDirectionMode(DirectionMode directionMode) { this.directionMode = directionMode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public ConnectionStatus getConnectionStatus() { return connectionStatus; }
    public void setConnectionStatus(ConnectionStatus connectionStatus) { this.connectionStatus = connectionStatus; }
    public Instant getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(Instant lastConnectedAt) { this.lastConnectedAt = lastConnectedAt; }
    public Instant getLastEventAt() { return lastEventAt; }
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
    public Integer getClockOffsetSeconds() { return clockOffsetSeconds; }
    public void setClockOffsetSeconds(Integer clockOffsetSeconds) { this.clockOffsetSeconds = clockOffsetSeconds; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
