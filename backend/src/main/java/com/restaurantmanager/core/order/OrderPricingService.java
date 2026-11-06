package com.restaurantmanager.core.order;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.loyalty.LoyaltyAccountService;
import com.restaurantmanager.core.promotion.MenuOfferEntity;
import com.restaurantmanager.core.promotion.MenuOfferRepository;
import com.restaurantmanager.core.promotion.PromoCode;
import com.restaurantmanager.core.promotion.PromoCodeEntity;
import com.restaurantmanager.core.promotion.PromoCodeRepository;
import com.restaurantmanager.core.promotion.PromoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies discounts to a freshly created order, in this order:
 * Buy-X-Get-Y offers, then promo code (on the post-offer amount), then loyalty points.
 */
@Service
public class OrderPricingService {
    private final MenuOfferRepository menuOfferRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final LoyaltyAccountService loyaltyAccountService;
    private final PromoService promoService = new PromoService();

    public OrderPricingService(MenuOfferRepository menuOfferRepository,
                               PromoCodeRepository promoCodeRepository,
                               LoyaltyAccountService loyaltyAccountService) {
        this.menuOfferRepository = menuOfferRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.loyaltyAccountService = loyaltyAccountService;
    }

    @Transactional
    public void applyDiscounts(OrderEntity order, List<OrderItemEntity> items, String promoCode, Integer loyaltyPointsToRedeem) {
        BigDecimal subtotal = order.getSubtotal();
        Instant now = Instant.now();

        BigDecimal offerDiscount = offerDiscount(items, now).min(subtotal);
        order.setOfferDiscount(offerDiscount);

        BigDecimal afterOffers = subtotal.subtract(offerDiscount);
        if (promoCode != null && !promoCode.isBlank()) {
            PromoCodeEntity promo = promoCodeRepository.findByCodeIgnoreCase(promoCode.trim())
                    .orElseThrow(() -> new ApiException(400, "Invalid promo code"));
            PromoCode domain = toDomain(promo);
            BigDecimal promoDiscount = promoService.applyPromo(domain, afterOffers, now).min(afterOffers);
            if (promoCodeRepository.consumeUsage(promo.getId()) == 0) {
                throw new ApiException(400, "Promo code usage limit reached");
            }
            order.setPromoCode(promo.getCode());
            order.setPromoDiscount(promoDiscount);
        }

        int requestedPoints = loyaltyPointsToRedeem == null ? 0 : loyaltyPointsToRedeem;
        if (requestedPoints > 0) {
            if (order.getCustomerUserId() == null) {
                throw new ApiException(400, "Loyalty points can only be redeemed by a signed-in customer");
            }
            BigDecimal payable = afterOffers.subtract(order.getPromoDiscount()).add(order.getDeliveryFee());
            LoyaltyAccountService.OrderRedemption redemption = loyaltyAccountService.redeemForOrder(
                    order.getCustomerUserId(), requestedPoints, payable, order.getId().toString());
            order.setLoyaltyPointsRedeemed(redemption.pointsRedeemed());
            order.setLoyaltyDiscount(redemption.discount());
        }

        order.recalculateTotal();
    }

    /** Releases promo usage and returns redeemed loyalty points for an order cancelled/voided before payment. */
    @Transactional
    public void releaseDiscounts(OrderEntity order) {
        if (order.getPromoCode() != null) {
            promoCodeRepository.releaseUsage(order.getPromoCode());
        }
        if (order.getLoyaltyPointsRedeemed() > 0 && order.getCustomerUserId() != null) {
            loyaltyAccountService.restoreRedeemed(order.getCustomerUserId(), order.getLoyaltyPointsRedeemed(), order.getId().toString());
        }
    }

    BigDecimal offerDiscount(List<OrderItemEntity> items, Instant now) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<UUID, Integer> quantityByMenuItem = new HashMap<>();
        Map<UUID, BigDecimal> cheapestUnitByMenuItem = new HashMap<>();
        for (OrderItemEntity item : items) {
            UUID menuItemId = item.getMenuItem().getId();
            quantityByMenuItem.merge(menuItemId, item.getQuantity(), Integer::sum);
            cheapestUnitByMenuItem.merge(menuItemId, item.getPriceSnapshot(), BigDecimal::min);
        }

        List<MenuOfferEntity> offers = menuOfferRepository.findByMenuItem_IdInAndActiveTrue(quantityByMenuItem.keySet());
        Map<UUID, BigDecimal> bestByMenuItem = new HashMap<>();
        offers.stream()
                .filter(offer -> offer.isRunningAt(now))
                .forEach(offer -> {
                    UUID menuItemId = offer.getMenuItem().getId();
                    int purchased = quantityByMenuItem.getOrDefault(menuItemId, 0);
                    int free = freeItems(purchased, offer.getBuyQuantity(), offer.getFreeQuantity());
                    BigDecimal value = cheapestUnitByMenuItem.get(menuItemId).multiply(BigDecimal.valueOf(free));
                    bestByMenuItem.merge(menuItemId, value, BigDecimal::max);
                });
        return bestByMenuItem.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * "Buy X get Y" applied to a cart: every complete group of (X + Y) units contains Y free units, and a
     * partial group beyond X units makes the extra units free. The free units must be in the order.
     */
    static int freeItems(int purchased, int buyQty, int freeQty) {
        if (buyQty <= 0 || freeQty <= 0) {
            return 0;
        }
        int groupSize = buyQty + freeQty;
        int fullGroups = purchased / groupSize;
        int remainder = purchased % groupSize;
        return fullGroups * freeQty + Math.max(0, remainder - buyQty);
    }

    private PromoCode toDomain(PromoCodeEntity entity) {
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
