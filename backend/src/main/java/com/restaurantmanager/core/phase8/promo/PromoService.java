package com.restaurantmanager.core.phase8.promo;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.phase8.common.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class PromoService {
    public BigDecimal applyPromo(PromoCode promo, BigDecimal subtotal, Instant now) {
        if (!promo.isActive()) {
            throw new ApiException(400, "Invalid promo code");
        }
        if (promo.getExpiresAt().isBefore(now)) {
            throw new ApiException(400, "Promo code expired");
        }
        if (promo.getUsedCount() >= promo.getUsageLimit()) {
            throw new ApiException(400, "Promo code usage limit reached");
        }
        if (subtotal.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new ApiException(400, "Minimum order amount not met");
        }

        BigDecimal discount = promo.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(promo.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : promo.getDiscountValue();

        BigDecimal applied = discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        promo.incrementUsedCount();
        return applied;
    }
}
