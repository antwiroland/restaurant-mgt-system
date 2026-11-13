package com.restaurantmanager.core.promotion;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A menu offer, applied automatically when the cart qualifies. See {@link OfferType} for the kinds. An offer runs
 * while it is active, inside its optional date range, on its days of the week and inside its daily time window
 * (happy hour), and only at its branch when it has one.
 */
@Entity
@Table(name = "menu_offers")
public class MenuOfferEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", nullable = false, length = 30)
    private OfferType offerType = OfferType.BUY_X_GET_Y;

    /** The dish bought (BUY_X_*) or discounted (PERCENT_OFF). Null for a COMBO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id")
    private MenuItemEntity menuItem;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @Column(name = "free_quantity")
    private Integer freeQuantity;

    /** BUY_X_GET_OTHER: the dish given free. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "free_menu_item_id")
    private MenuItemEntity freeMenuItem;

    @Column(name = "percent_off", precision = 5, scale = 2)
    private BigDecimal percentOff;

    @Column(name = "combo_price", precision = 12, scale = 2)
    private BigDecimal comboPrice;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "menu_offer_combo_items", joinColumns = @JoinColumn(name = "offer_id"))
    private List<ComboItem> comboItems = new ArrayList<>();

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /** Comma-separated MON..SUN; null or empty means every day. */
    @Column(name = "days_of_week", length = 40)
    private String daysOfWeek;

    @Column(name = "daily_start_time")
    private LocalTime dailyStartTime;

    @Column(name = "daily_end_time")
    private LocalTime dailyEndTime;

    /** Null: every branch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private BranchEntity branch;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** One dish in a combo and how many of it the combo needs. */
    @Embeddable
    public static class ComboItem {
        @Column(name = "menu_item_id", nullable = false)
        private UUID menuItemId;

        @Column(nullable = false)
        private int quantity;

        protected ComboItem() {
        }

        public ComboItem(UUID menuItemId, int quantity) {
            this.menuItemId = menuItemId;
            this.quantity = quantity;
        }

        public UUID getMenuItemId() { return menuItemId; }
        public int getQuantity() { return quantity; }
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    /** Running at this instant, by date range only (no day or hour rules): kept for callers without a time zone. */
    public boolean isRunningAt(Instant now) {
        return active
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || endsAt.isAfter(now));
    }

    /** Running at this instant in the venue's time zone: date range, days of the week and the daily time window. */
    public boolean isRunningAt(Instant now, ZoneId zone) {
        if (!isRunningAt(now)) {
            return false;
        }
        ZonedDateTime local = now.atZone(zone);
        LocalTime time = local.toLocalTime();
        Set<DayOfWeek> days = days();
        if (dailyStartTime != null && dailyEndTime != null && dailyEndTime.isBefore(dailyStartTime)) {
            // Overnight window (e.g. 22:00-02:00): after midnight it still belongs to the previous day's session.
            if (!time.isBefore(dailyStartTime)) {
                return days.isEmpty() || days.contains(local.getDayOfWeek());
            }
            if (time.isBefore(dailyEndTime)) {
                return days.isEmpty() || days.contains(local.getDayOfWeek().minus(1));
            }
            return false;
        }
        if (!days.isEmpty() && !days.contains(local.getDayOfWeek())) {
            return false;
        }
        if (dailyStartTime != null && time.isBefore(dailyStartTime)) {
            return false;
        }
        return dailyEndTime == null || time.isBefore(dailyEndTime);
    }

    /** Applies to an order at this branch. An unknown branch only gets offers that run everywhere. */
    public boolean appliesToBranch(UUID branchId) {
        return branch == null || (branchId != null && branch.getId().equals(branchId));
    }

    /** Not finished for good: active and not past its end date. Such offers are shown to customers. */
    public boolean isAdvertisableAt(Instant now) {
        return active && (endsAt == null || endsAt.isAfter(now));
    }

    public Set<DayOfWeek> days() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return days;
        }
        for (String part : daysOfWeek.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);
            for (DayOfWeek day : DayOfWeek.values()) {
                if (day.name().startsWith(code) && !code.isEmpty()) {
                    days.add(day);
                }
            }
        }
        return days;
    }

    /** Every dish the offer involves (for badges on the menu). */
    public List<UUID> menuItemIds() {
        List<UUID> ids = new ArrayList<>();
        if (menuItem != null) ids.add(menuItem.getId());
        if (freeMenuItem != null && !ids.contains(freeMenuItem.getId())) ids.add(freeMenuItem.getId());
        for (ComboItem item : comboItems) {
            if (!ids.contains(item.getMenuItemId())) ids.add(item.getMenuItemId());
        }
        return ids;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OfferType getOfferType() { return offerType; }
    public void setOfferType(OfferType offerType) { this.offerType = offerType; }
    public MenuItemEntity getMenuItem() { return menuItem; }
    public void setMenuItem(MenuItemEntity menuItem) { this.menuItem = menuItem; }
    public int getBuyQuantity() { return buyQuantity == null ? 0 : buyQuantity; }
    public void setBuyQuantity(Integer buyQuantity) { this.buyQuantity = buyQuantity; }
    public int getFreeQuantity() { return freeQuantity == null ? 0 : freeQuantity; }
    public void setFreeQuantity(Integer freeQuantity) { this.freeQuantity = freeQuantity; }
    public MenuItemEntity getFreeMenuItem() { return freeMenuItem; }
    public void setFreeMenuItem(MenuItemEntity freeMenuItem) { this.freeMenuItem = freeMenuItem; }
    public BigDecimal getPercentOff() { return percentOff; }
    public void setPercentOff(BigDecimal percentOff) { this.percentOff = percentOff; }
    public BigDecimal getComboPrice() { return comboPrice; }
    public void setComboPrice(BigDecimal comboPrice) { this.comboPrice = comboPrice; }
    public List<ComboItem> getComboItems() { return comboItems; }
    public void setComboItems(List<ComboItem> comboItems) {
        this.comboItems.clear();
        this.comboItems.addAll(comboItems);
    }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public LocalTime getDailyStartTime() { return dailyStartTime; }
    public void setDailyStartTime(LocalTime dailyStartTime) { this.dailyStartTime = dailyStartTime; }
    public LocalTime getDailyEndTime() { return dailyEndTime; }
    public void setDailyEndTime(LocalTime dailyEndTime) { this.dailyEndTime = dailyEndTime; }
    public BranchEntity getBranch() { return branch; }
    public void setBranch(BranchEntity branch) { this.branch = branch; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
