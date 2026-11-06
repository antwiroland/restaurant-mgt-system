package com.restaurantmanager.core.table.qr;

import com.restaurantmanager.core.table.RestaurantTableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "table_mobile_sessions")
public class TableMobileSessionEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTableEntity table;

    @Column(name = "session_id", nullable = false, length = 120)
    private String sessionId;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (linkedAt == null) {
            linkedAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    public UUID getId() { return id; }
    public RestaurantTableEntity getTable() { return table; }
    public void setTable(RestaurantTableEntity table) { this.table = table; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public UUID getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(UUID customerUserId) { this.customerUserId = customerUserId; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
