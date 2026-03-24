package com.restaurantmanager.core.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_order_sessions")
public class GroupOrderSessionEntity {
    @Id
    private UUID id;

    @Column(name = "session_code", nullable = false, unique = true, length = 20)
    private String sessionCode;

    @Column(name = "host_user_id", nullable = false)
    private UUID hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupSessionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = GroupSessionStatus.OPEN;
        }
        createdAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public UUID getHostUserId() {
        return hostUserId;
    }

    public void setHostUserId(UUID hostUserId) {
        this.hostUserId = hostUserId;
    }

    public GroupSessionStatus getStatus() {
        return status;
    }

    public void setStatus(GroupSessionStatus status) {
        this.status = status;
    }
}
