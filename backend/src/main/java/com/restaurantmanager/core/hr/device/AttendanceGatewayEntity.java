package com.restaurantmanager.core.hr.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A local attendance gateway: the program on the restaurant network that reads the biometric devices. */
@Entity
@Table(name = "hr_attendance_gateways")
public class AttendanceGatewayEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "gateway_identifier", nullable = false, unique = true, length = 60)
    private String gatewayIdentifier;

    /** SHA-256 of the full key; the key itself is shown once when issued. */
    @Column(name = "key_hash", nullable = false, length = 128)
    private String keyHash;

    /** The key's non-secret start, used to find the gateway and shown in the admin screen. */
    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "pending_events", nullable = false)
    private int pendingEvents;

    @Column(name = "failed_sync_attempts", nullable = false)
    private int failedSyncAttempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "software_version", length = 40)
    private String softwareVersion;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGatewayIdentifier() { return gatewayIdentifier; }
    public void setGatewayIdentifier(String gatewayIdentifier) { this.gatewayIdentifier = gatewayIdentifier; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public int getPendingEvents() { return pendingEvents; }
    public void setPendingEvents(int pendingEvents) { this.pendingEvents = pendingEvents; }
    public int getFailedSyncAttempts() { return failedSyncAttempts; }
    public void setFailedSyncAttempts(int failedSyncAttempts) { this.failedSyncAttempts = failedSyncAttempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
