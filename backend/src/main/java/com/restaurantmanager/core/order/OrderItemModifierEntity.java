package com.restaurantmanager.core.order;

import com.restaurantmanager.core.menu.MenuModifierOptionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_item_modifiers")
public class OrderItemModifierEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItemEntity orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_option_id")
    private MenuModifierOptionEntity modifierOption;

    @Column(name = "group_name_snapshot", nullable = false, length = 120)
    private String groupNameSnapshot;

    @Column(name = "option_name_snapshot", nullable = false, length = 120)
    private String optionNameSnapshot;

    @Column(name = "price_delta_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceDeltaSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OrderItemEntity getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(OrderItemEntity orderItem) {
        this.orderItem = orderItem;
    }

    public MenuModifierOptionEntity getModifierOption() {
        return modifierOption;
    }

    public void setModifierOption(MenuModifierOptionEntity modifierOption) {
        this.modifierOption = modifierOption;
    }

    public String getGroupNameSnapshot() {
        return groupNameSnapshot;
    }

    public void setGroupNameSnapshot(String groupNameSnapshot) {
        this.groupNameSnapshot = groupNameSnapshot;
    }

    public String getOptionNameSnapshot() {
        return optionNameSnapshot;
    }

    public void setOptionNameSnapshot(String optionNameSnapshot) {
        this.optionNameSnapshot = optionNameSnapshot;
    }

    public BigDecimal getPriceDeltaSnapshot() {
        return priceDeltaSnapshot;
    }

    public void setPriceDeltaSnapshot(BigDecimal priceDeltaSnapshot) {
        this.priceDeltaSnapshot = priceDeltaSnapshot;
    }
}
