package com.restaurantmanager.core.hr.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "hr_user_permissions")
@IdClass(HrUserPermissionEntity.Key.class)
public class HrUserPermissionEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 50)
    private HrPermission permission;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected HrUserPermissionEntity() {
    }

    public HrUserPermissionEntity(UUID userId, HrPermission permission, UUID grantedBy) {
        this.userId = userId;
        this.permission = permission;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void prePersist() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public HrPermission getPermission() {
        return permission;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private HrPermission permission;

        public Key() {
        }

        public Key(UUID userId, HrPermission permission) {
            this.userId = userId;
            this.permission = permission;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && permission == k.permission;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, permission);
        }
    }
}
