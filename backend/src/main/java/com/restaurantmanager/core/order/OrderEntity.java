package com.restaurantmanager.core.order;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "orders")
public class OrderEntity {
    @Id
    private UUID id;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTableEntity table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private BranchEntity branch;

    @Column(name = "delivery_address", length = 1000)
    private String deliveryAddress;

    @Column(name = "pickup_time")
    private Instant pickupTime;

    @Column(name = "pickup_code", length = 20)
    private String pickupCode;

    @Column(name = "estimated_delivery_time")
    private Instant estimatedDeliveryTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_session_id")
    private GroupOrderSessionEntity groupSession;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "promo_code", length = 30)
    private String promoCode;

    @Column(name = "promo_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal promoDiscount = BigDecimal.ZERO;

    @Column(name = "offer_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal offerDiscount = BigDecimal.ZERO;

    @Column(name = "loyalty_points_redeemed", nullable = false)
    private int loyaltyPointsRedeemed;

    @Column(name = "loyalty_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal loyaltyDiscount = BigDecimal.ZERO;

    @Column(name = "loyalty_points_awarded", nullable = false)
    private int loyaltyPointsAwarded;

    @Column(name = "manual_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal manualDiscount = BigDecimal.ZERO;

    @Column(name = "manual_discount_reason", length = 500)
    private String manualDiscountReason;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "delivery_zone_id")
    private UUID deliveryZoneId;

    @Column(name = "rider_user_id")
    private UUID riderUserId;

    @Column(name = "rider_assigned_at")
    private Instant riderAssignedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "rider_latitude")
    private Double riderLatitude;

    @Column(name = "rider_longitude")
    private Double riderLongitude;

    @Column(name = "rider_location_at")
    private Instant riderLocationAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = OrderStatus.PENDING;
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

    public UUID getCustomerUserId() {
        return customerUserId;
    }

    public void setCustomerUserId(UUID customerUserId) {
        this.customerUserId = customerUserId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public RestaurantTableEntity getTable() {
        return table;
    }

    public void setTable(RestaurantTableEntity table) {
        this.table = table;
    }

    public BranchEntity getBranch() {
        return branch;
    }

    public void setBranch(BranchEntity branch) {
        this.branch = branch;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public Instant getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(Instant pickupTime) {
        this.pickupTime = pickupTime;
    }

    public String getPickupCode() {
        return pickupCode;
    }

    public void setPickupCode(String pickupCode) {
        this.pickupCode = pickupCode;
    }

    public Instant getEstimatedDeliveryTime() {
        return estimatedDeliveryTime;
    }

    public void setEstimatedDeliveryTime(Instant estimatedDeliveryTime) {
        this.estimatedDeliveryTime = estimatedDeliveryTime;
    }

    public GroupOrderSessionEntity getGroupSession() {
        return groupSession;
    }

    public void setGroupSession(GroupOrderSessionEntity groupSession) {
        this.groupSession = groupSession;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public BigDecimal getPromoDiscount() {
        return promoDiscount;
    }

    public void setPromoDiscount(BigDecimal promoDiscount) {
        this.promoDiscount = promoDiscount;
    }

    public BigDecimal getOfferDiscount() {
        return offerDiscount;
    }

    public void setOfferDiscount(BigDecimal offerDiscount) {
        this.offerDiscount = offerDiscount;
    }

    public int getLoyaltyPointsRedeemed() {
        return loyaltyPointsRedeemed;
    }

    public void setLoyaltyPointsRedeemed(int loyaltyPointsRedeemed) {
        this.loyaltyPointsRedeemed = loyaltyPointsRedeemed;
    }

    public BigDecimal getLoyaltyDiscount() {
        return loyaltyDiscount;
    }

    public void setLoyaltyDiscount(BigDecimal loyaltyDiscount) {
        this.loyaltyDiscount = loyaltyDiscount;
    }

    public int getLoyaltyPointsAwarded() {
        return loyaltyPointsAwarded;
    }

    public void setLoyaltyPointsAwarded(int loyaltyPointsAwarded) {
        this.loyaltyPointsAwarded = loyaltyPointsAwarded;
    }

    public BigDecimal getManualDiscount() {
        return manualDiscount;
    }

    public void setManualDiscount(BigDecimal manualDiscount) {
        this.manualDiscount = manualDiscount;
    }

    public String getManualDiscountReason() {
        return manualDiscountReason;
    }

    public void setManualDiscountReason(String manualDiscountReason) {
        this.manualDiscountReason = manualDiscountReason;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(Instant voidedAt) {
        this.voidedAt = voidedAt;
    }

    public UUID getVoidedBy() {
        return voidedBy;
    }

    public void setVoidedBy(UUID voidedBy) {
        this.voidedBy = voidedBy;
    }

    public BigDecimal totalDiscount() {
        return promoDiscount.add(offerDiscount).add(loyaltyDiscount).add(manualDiscount);
    }

    /** total = subtotal + deliveryFee - all discounts, never below zero. */
    public void recalculateTotal() {
        BigDecimal base = subtotal == null ? BigDecimal.ZERO : subtotal;
        BigDecimal computed = base.add(deliveryFee).subtract(totalDiscount());
        total = (computed.signum() < 0 ? BigDecimal.ZERO : computed).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public UUID getDeliveryZoneId() {
        return deliveryZoneId;
    }

    public void setDeliveryZoneId(UUID deliveryZoneId) {
        this.deliveryZoneId = deliveryZoneId;
    }

    public UUID getRiderUserId() {
        return riderUserId;
    }

    public void setRiderUserId(UUID riderUserId) {
        this.riderUserId = riderUserId;
    }

    public Instant getRiderAssignedAt() {
        return riderAssignedAt;
    }

    public void setRiderAssignedAt(Instant riderAssignedAt) {
        this.riderAssignedAt = riderAssignedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Double getRiderLatitude() {
        return riderLatitude;
    }

    public void setRiderLatitude(Double riderLatitude) {
        this.riderLatitude = riderLatitude;
    }

    public Double getRiderLongitude() {
        return riderLongitude;
    }

    public void setRiderLongitude(Double riderLongitude) {
        this.riderLongitude = riderLongitude;
    }

    public Instant getRiderLocationAt() {
        return riderLocationAt;
    }

    public void setRiderLocationAt(Instant riderLocationAt) {
        this.riderLocationAt = riderLocationAt;
    }
}
