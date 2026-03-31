package com.restaurantmanager.core.phase8.promo;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.phase8.common.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class PromoService {
    public void validatePromo(PromoCode promo, BigDecimal subtotal, Instant now) {
        if (!promo.isActive()) {
            throw new ApiException(400, "Invalid promo code");
        }
        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(now)) {
            throw new ApiException(400, "Promo code expired");
        }
        if (promo.getUsageLimit() != null && promo.getUsageCount() >= promo.getUsageLimit()) {
            throw new ApiException(400, "Promo code usage limit reached");
        }
        if (subtotal.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new ApiException(400, "Minimum order amount not met");
        }
    }

    public BigDecimal applyPromo(PromoCode promo, BigDecimal subtotal, Instant now) {
        validatePromo(promo, subtotal, now);

        BigDecimal discount = promo.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(promo.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : promo.getDiscountValue();
        if (promo.getMaxDiscount() != null) {
            discount = discount.min(promo.getMaxDiscount());
        }

        BigDecimal applied = discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        promo.incrementUsedCount();
        return applied;
    }
}
