package com.restaurantmanager.core.promotion;

import com.restaurantmanager.core.common.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PromotionService {
    private final PromoCodeRepository promoCodeRepository;
    private final PromoService promoService = new PromoService();
    private final BuyXGetYOfferService offerService = new BuyXGetYOfferService();

    public PromotionService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    public BigDecimal applyPromo(String code,
                                 DiscountType discountType,
                                 BigDecimal discountValue,
                                 BigDecimal minOrderAmount,
                                 Instant expiresAt,
                                 int usageLimit,
                                 int usedCount,
                                 boolean active,
                                 BigDecimal subtotal,
                                 Instant now) {
        PromoCode promo = new PromoCode(code, discountType, discountValue, minOrderAmount, expiresAt, usageLimit, usedCount, active);
        return promoService.applyPromo(promo, subtotal, now);
    }

    public PromoCode validatePromo(String code, BigDecimal subtotal, Instant now) {
        PromoCode promo = loadPromo(code);
        promoService.validatePromo(promo, subtotal, now);
        return promo;
    }

    public int freeItemsFor(int purchasedQuantity, int buyQty, int getQty) {
        return offerService.freeItemsFor(purchasedQuantity, buyQty, getQty);
    }

    private PromoCode loadPromo(String code) {
        PromoCodeEntity entity = promoCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(404, "Promo code not found"));
        return toPromoCode(entity);
    }

    static PromoCode toPromoCode(PromoCodeEntity entity) {
        return new PromoCode(
                entity.getCode(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMinOrderAmount(),
                entity.getMaxDiscount(),
                entity.getExpiryDate(),
                entity.getUsageLimit(),
                entity.getUsageCount(),
                entity.isActive()
        );
    }
}
