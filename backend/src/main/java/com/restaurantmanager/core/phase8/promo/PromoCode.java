package com.restaurantmanager.core.phase8.promo;

import com.restaurantmanager.core.phase8.common.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public class PromoCode {
    private final String code;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal minOrderAmount;
    private final BigDecimal maxDiscount;
    private final Instant expiresAt;
    private final Integer usageLimit;
    private int usageCount;
    private boolean active;

    public PromoCode(String code,
                     DiscountType discountType,
                     BigDecimal discountValue,
                     BigDecimal minOrderAmount,
                     Instant expiresAt,
                     int usageLimit,
                     int usageCount,
                     boolean active) {
        this(code, discountType, discountValue, minOrderAmount, null, expiresAt, usageLimit, usageCount, active);
    }

    public PromoCode(String code,
                     DiscountType discountType,
                     BigDecimal discountValue,
                     BigDecimal minOrderAmount,
                     BigDecimal maxDiscount,
                     Instant expiresAt,
                     Integer usageLimit,
                     int usageCount,
                     boolean active) {
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscount = maxDiscount;
        this.expiresAt = expiresAt;
        this.usageLimit = usageLimit;
        this.usageCount = usageCount;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void incrementUsedCount() {
        usageCount += 1;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
