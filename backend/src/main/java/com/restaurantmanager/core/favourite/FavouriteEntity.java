package com.restaurantmanager.core.favourite;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "favourites")
public class FavouriteEntity {
    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FavouriteEntity() {
    }

    public FavouriteEntity(UUID userId, UUID menuItemId) {
        this.id = new Key(userId, menuItemId);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Key getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "menu_item_id", nullable = false)
        private UUID menuItemId;

        protected Key() {
        }

        public Key(UUID userId, UUID menuItemId) {
            this.userId = userId;
            this.menuItemId = menuItemId;
        }

        public UUID getUserId() { return userId; }
        public UUID getMenuItemId() { return menuItemId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(menuItemId, key.menuItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, menuItemId);
        }
    }
}
