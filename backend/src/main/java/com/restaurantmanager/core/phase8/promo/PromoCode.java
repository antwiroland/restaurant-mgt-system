package com.restaurantmanager.core.phase8.promo;

import com.restaurantmanager.core.phase8.common.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public class PromoCode {
    private final String code;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal minOrderAmount;
    private final Instant expiresAt;
    private final int usageLimit;
    private int usedCount;
    private boolean active;

    public PromoCode(String code,
                     DiscountType discountType,
                     BigDecimal discountValue,
                     BigDecimal minOrderAmount,
                     Instant expiresAt,
                     int usageLimit,
                     int usedCount,
                     boolean active) {
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiresAt = expiresAt;
        this.usageLimit = usageLimit;
        this.usedCount = usedCount;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getUsageLimit() {
        return usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void incrementUsedCount() {
        usedCount += 1;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
