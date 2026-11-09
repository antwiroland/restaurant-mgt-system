package com.restaurantmanager.core.hr.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Extra branch added to a non-admin user's HR scope (e.g. an area manager covering two branches). */
@Entity
@Table(name = "hr_user_branch_access")
@IdClass(HrUserBranchAccessEntity.Key.class)
public class HrUserBranchAccessEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected HrUserBranchAccessEntity() {
    }

    public HrUserBranchAccessEntity(UUID userId, UUID branchId, UUID grantedBy) {
        this.userId = userId;
        this.branchId = branchId;
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

    public UUID getBranchId() {
        return branchId;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private UUID branchId;

        public Key() {
        }

        public Key(UUID userId, UUID branchId) {
            this.userId = userId;
            this.branchId = branchId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(branchId, k.branchId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, branchId);
        }
    }
}
