package com.restaurantmanager.core.order;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.loyalty.LoyaltyAccountService;
import com.restaurantmanager.core.promotion.MenuOfferEntity;
import com.restaurantmanager.core.promotion.MenuOfferRepository;
import com.restaurantmanager.core.promotion.OfferEngine;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import org.springframework.beans.factory.annotation.Value;
import com.restaurantmanager.core.promotion.PromoCode;
import com.restaurantmanager.core.promotion.PromoCodeEntity;
import com.restaurantmanager.core.promotion.PromoCodeRepository;
import com.restaurantmanager.core.promotion.PromoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies discounts to a freshly created order, in this order:
 * menu offers (see {@link OfferEngine}), then promo code (on the post-offer amount), then loyalty points.
 */
@Service
public class OrderPricingService {
    private final MenuOfferRepository menuOfferRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final LoyaltyAccountService loyaltyAccountService;
    private final com.restaurantmanager.core.billing.BillingService billingService;
    private final PromoService promoService = new PromoService();
    private final MenuItemRepository menuItemRepository;
    private final ZoneId zone;

    public OrderPricingService(MenuOfferRepository menuOfferRepository,
                               PromoCodeRepository promoCodeRepository,
                               LoyaltyAccountService loyaltyAccountService,
                               com.restaurantmanager.core.billing.BillingService billingService,
                               MenuItemRepository menuItemRepository,
                               @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.menuItemRepository = menuItemRepository;
        this.zone = ZoneId.of(timeZone);
        this.menuOfferRepository = menuOfferRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.loyaltyAccountService = loyaltyAccountService;
        this.billingService = billingService;
    }

    @Transactional
    public void applyDiscounts(OrderEntity order, List<OrderItemEntity> items, String promoCode, Integer loyaltyPointsToRedeem) {
        BigDecimal subtotal = order.getSubtotal();
        Instant now = Instant.now();

        BigDecimal offerDiscount = offers(items, branchId(order), now).discount().min(subtotal);
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

        billingService.apply(order);
    }

    @Transactional
    public void setTip(OrderEntity order, BigDecimal amount, BigDecimal percent) {
        billingService.setTip(order, amount, percent);
    }

    @Transactional(readOnly = true)
    public java.util.Map<java.util.UUID, List<com.restaurantmanager.core.billing.BillingService.TaxLine>> taxLines(
            java.util.Collection<java.util.UUID> orderIds) {
        return billingService.taxLines(orderIds);
    }

    /** Recomputes service charge, taxes and total after any change to discounts, fees or tip. */
    @Transactional
    public void reprice(OrderEntity order) {
        billingService.apply(order);
    }

    /**
     * Side-effect free estimate for a cart: offers, promo (validated, not consumed), loyalty (capped by the
     * customer's balance), then service charge and taxes. Fills the given transient order.
     */
    @Transactional(readOnly = true)
    public Quote quote(OrderEntity order, List<OrderItemEntity> items, String promoCode, Integer loyaltyPointsToRedeem) {
        BigDecimal subtotal = order.getSubtotal();
        Instant now = Instant.now();
        OfferEngine.Result offers = offers(items, branchId(order), now);
        BigDecimal offer = offers.discount().min(subtotal);
        order.setOfferDiscount(offer);
        BigDecimal afterOffers = subtotal.subtract(offer);
        if (promoCode != null && !promoCode.isBlank()) {
            PromoCodeEntity promo = promoCodeRepository.findByCodeIgnoreCase(promoCode.trim())
                    .orElseThrow(() -> new ApiException(400, "Invalid promo code"));
            order.setPromoCode(promo.getCode());
            order.setPromoDiscount(promoService.applyPromo(toDomain(promo), afterOffers, now).min(afterOffers));
        }
        int points = loyaltyPointsToRedeem == null ? 0 : loyaltyPointsToRedeem;
        if (points > 0 && order.getCustomerUserId() != null) {
            BigDecimal payable = afterOffers.subtract(order.getPromoDiscount()).add(order.getDeliveryFee());
            var props = loyaltyAccountService.props();
            BigDecimal cap = payable.multiply(BigDecimal.valueOf(props.getMaxRedeemPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
            int usable = Math.min(points, loyaltyAccountService.balance(order.getCustomerUserId()));
            BigDecimal discount = props.getPointValue().multiply(BigDecimal.valueOf(usable)).min(cap).setScale(2, RoundingMode.DOWN);
            order.setLoyaltyDiscount(discount);
            order.setLoyaltyPointsRedeemed(discount.divide(props.getPointValue(), 0, RoundingMode.DOWN).intValue());
        }
        order.recalculateTotal();
        var breakdown = billingService.preview(order.getType(), order.discountedSubtotal(), order.getDeliveryFee());
        order.setServiceCharge(breakdown.serviceCharge());
        order.setTaxTotal(breakdown.taxTotal());
        order.setTaxInclusive(breakdown.taxInclusive());
        order.recalculateTotal();
        return new Quote(breakdown, offers.applied(), offers.hints());
    }

    /** A cart estimate: taxes and charges, the offers that applied, and suggestions to complete a deal. */
    public record Quote(com.restaurantmanager.core.billing.BillingService.Breakdown breakdown,
                        List<OfferEngine.AppliedOffer> appliedOffers,
                        List<OfferEngine.Hint> offerHints) {
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

    /** Offers for these items at this branch (null: unknown, so only offers that run everywhere) and time. */
    OfferEngine.Result offers(List<OrderItemEntity> items, UUID branchId, Instant now) {
        if (items.isEmpty()) {
            return new OfferEngine.Result(BigDecimal.ZERO, List.of(), List.of());
        }
        List<MenuOfferEntity> running = menuOfferRepository.findActiveForPricing().stream()
                .filter(offer -> offer.isRunningAt(now, zone))
                .filter(offer -> offer.appliesToBranch(branchId))
                .toList();
        if (running.isEmpty()) {
            return new OfferEngine.Result(BigDecimal.ZERO, List.of(), List.of());
        }
        Map<UUID, String> names = new HashMap<>();
        List<OfferEngine.CartLine> lines = new java.util.ArrayList<>();
        for (OrderItemEntity item : items) {
            names.put(item.getMenuItem().getId(), item.getMenuItem().getName());
            lines.add(new OfferEngine.CartLine(item.getMenuItem().getId(), item.getPriceSnapshot(), item.getQuantity()));
        }
        java.util.Set<UUID> unnamed = new java.util.HashSet<>();
        running.forEach(offer -> offer.menuItemIds().stream().filter(id -> !names.containsKey(id)).forEach(unnamed::add));
        menuItemRepository.findAllById(unnamed).forEach(dish -> names.put(dish.getId(), dish.getName()));
        return OfferEngine.evaluate(lines, running, id -> names.getOrDefault(id, "item"));
    }

    private static UUID branchId(OrderEntity order) {
        return order.getBranch() == null ? null : order.getBranch().getId();
    }

    /** "Buy X get Y" on a number of units; see {@link OfferEngine#freeItems}. */
    static int freeItems(int purchased, int buyQty, int freeQty) {
        return OfferEngine.freeItems(purchased, buyQty, freeQty);
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
