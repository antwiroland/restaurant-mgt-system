package com.restaurantmanager.core.order;

import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuModifierOptionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "group_session_items")
public class GroupSessionItemEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GroupOrderSessionEntity session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private GroupSessionParticipantEntity participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItemEntity menuItem;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 1000)
    private String notes;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "group_session_item_modifiers",
            joinColumns = @JoinColumn(name = "group_session_item_id"),
            inverseJoinColumns = @JoinColumn(name = "modifier_option_id"))
    private List<MenuModifierOptionEntity> modifierOptions = new ArrayList<>();

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
        createdAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GroupOrderSessionEntity getSession() {
        return session;
    }

    public void setSession(GroupOrderSessionEntity session) {
        this.session = session;
    }

    public GroupSessionParticipantEntity getParticipant() {
        return participant;
    }

    public void setParticipant(GroupSessionParticipantEntity participant) {
        this.participant = participant;
    }

    public MenuItemEntity getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItemEntity menuItem) {
        this.menuItem = menuItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<MenuModifierOptionEntity> getModifierOptions() {
        return modifierOptions;
    }

    public void setModifierOptions(List<MenuModifierOptionEntity> modifierOptions) {
        this.modifierOptions = new ArrayList<>(modifierOptions);
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
