package com.restaurantmanager.core.phase8.runtime;

import com.restaurantmanager.core.phase8.common.DiscountType;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyAccount;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyService;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyTransaction;
import com.restaurantmanager.core.phase8.offer.BuyXGetYOfferService;
import com.restaurantmanager.core.phase8.promo.PromoCode;
import com.restaurantmanager.core.phase8.promo.PromoService;
import com.restaurantmanager.core.phase8.qr.MobileSessionService;
import com.restaurantmanager.core.phase8.qr.TableQrPdfService;
import com.restaurantmanager.core.phase8.whatsapp.WhatsAppService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class Phase8RuntimeService {
    private final PromoService promoService = new PromoService();
    private final BuyXGetYOfferService offerService = new BuyXGetYOfferService();
    private final LoyaltyService loyaltyService = new LoyaltyService(1);
    private final MobileSessionService mobileSessionService = new MobileSessionService();
    private final TableQrPdfService tableQrPdfService = new TableQrPdfService();
    private final WhatsAppService whatsAppService = new WhatsAppService((phone, message) -> {}, __ -> {});
    private final ConcurrentMap<UUID, LoyaltyAccount> loyaltyAccounts = new ConcurrentHashMap<>();

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
        PromoCode promo = new PromoCode(
                code,
                discountType,
                discountValue,
                minOrderAmount,
                expiresAt,
                usageLimit,
                usedCount,
                active
        );
        return promoService.applyPromo(promo, subtotal, now);
    }

    public int freeItemsFor(int purchasedQuantity, int buyQty, int getQty) {
        return offerService.freeItemsFor(purchasedQuantity, buyQty, getQty);
    }

    public int accrue(UUID customerId, BigDecimal paymentAmount, String orderId) {
        LoyaltyAccount account = loyaltyAccounts.computeIfAbsent(customerId, ignored -> new LoyaltyAccount());
        return loyaltyService.accrue(account, paymentAmount, orderId);
    }

    public LoyaltyService.RedeemResult redeem(UUID customerId, int pointsToRedeem, BigDecimal orderTotal, String orderId) {
        LoyaltyAccount account = loyaltyAccounts.computeIfAbsent(customerId, ignored -> new LoyaltyAccount());
        return loyaltyService.redeem(account, pointsToRedeem, orderTotal, orderId);
    }

    public int balance(UUID customerId) {
        LoyaltyAccount account = loyaltyAccounts.computeIfAbsent(customerId, ignored -> new LoyaltyAccount());
        return loyaltyService.currentBalance(account);
    }

    public List<LoyaltyTransaction> history(UUID customerId) {
        LoyaltyAccount account = loyaltyAccounts.computeIfAbsent(customerId, ignored -> new LoyaltyAccount());
        return loyaltyService.history(account);
    }

    public void linkSession(String tableNumber, String sessionId) {
        mobileSessionService.linkSession(tableNumber, sessionId);
    }

    public boolean hasActiveSession(String tableNumber) {
        return mobileSessionService.hasActiveSession(tableNumber);
    }

    public void closeSession(String tableNumber) {
        mobileSessionService.closeSession(tableNumber);
    }

    public byte[] exportTableQrPdf(String tableNumber) {
        return tableQrPdfService.exportTableQrPdf(tableNumber);
    }

    public boolean sendOrderConfirmation(String phone, String orderId) {
        return whatsAppService.sendOrderConfirmation(phone, orderId);
    }

    public boolean sendReceipt(String phone, String receiptLink, String reorderLink) {
        return whatsAppService.sendReceipt(phone, receiptLink, reorderLink);
    }

    public boolean sendStatusUpdate(String phone, String status) {
        return whatsAppService.sendStatusUpdate(phone, status);
    }
}
