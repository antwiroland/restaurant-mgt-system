package com.restaurantmanager.core.phase8.runtime;

import com.restaurantmanager.core.phase8.common.DiscountType;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyAccount;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyBalanceEntity;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyBalanceRepository;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyService;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyTransaction;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyTransactionEntity;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyTransactionRepository;
import com.restaurantmanager.core.phase8.offer.BuyXGetYOfferService;
import com.restaurantmanager.core.phase8.promo.PromoCode;
import com.restaurantmanager.core.phase8.promo.PromoCodeEntity;
import com.restaurantmanager.core.phase8.promo.PromoCodeRepository;
import com.restaurantmanager.core.phase8.promo.PromoService;
import com.restaurantmanager.core.phase8.qr.MobileSessionService;
import com.restaurantmanager.core.phase8.qr.TableQrPdfService;
import com.restaurantmanager.core.phase8.whatsapp.WhatsAppService;
import com.restaurantmanager.core.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class Phase8RuntimeService {
    private final PromoCodeRepository promoCodeRepository;
    private final LoyaltyBalanceRepository loyaltyBalanceRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final PromoService promoService = new PromoService();
    private final BuyXGetYOfferService offerService = new BuyXGetYOfferService();
    private final LoyaltyService loyaltyService = new LoyaltyService(1);
    private final MobileSessionService mobileSessionService = new MobileSessionService();
    private final TableQrPdfService tableQrPdfService = new TableQrPdfService();
    private final WhatsAppService whatsAppService = new WhatsAppService((phone, message) -> {}, __ -> {});

    public Phase8RuntimeService(PromoCodeRepository promoCodeRepository,
                                LoyaltyBalanceRepository loyaltyBalanceRepository,
                                LoyaltyTransactionRepository loyaltyTransactionRepository) {
        this.promoCodeRepository = promoCodeRepository;
        this.loyaltyBalanceRepository = loyaltyBalanceRepository;
        this.loyaltyTransactionRepository = loyaltyTransactionRepository;
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

    public PromoCode validatePromo(String code, BigDecimal subtotal, Instant now) {
        PromoCode promo = loadPromo(code);
        promoService.validatePromo(promo, subtotal, now);
        return promo;
    }

    public int freeItemsFor(int purchasedQuantity, int buyQty, int getQty) {
        return offerService.freeItemsFor(purchasedQuantity, buyQty, getQty);
    }

    @Transactional
    public int accrue(UUID customerId, BigDecimal paymentAmount, String orderId) {
        LoyaltyBalanceEntity balance = findOrCreateBalance(customerId);
        LoyaltyAccount account = toAccount(balance);

        int awarded = loyaltyService.accrue(account, paymentAmount, orderId);

        balance.setPoints(account.getPoints());
        loyaltyBalanceRepository.save(balance);
        saveTransaction(account.getTransactions().get(0), customerId);

        return awarded;
    }

    @Transactional
    public LoyaltyService.RedeemResult redeem(UUID customerId, int pointsToRedeem, BigDecimal orderTotal, String orderId) {
        LoyaltyBalanceEntity balance = findOrCreateBalance(customerId);
        LoyaltyAccount account = toAccount(balance);

        LoyaltyService.RedeemResult result = loyaltyService.redeem(account, pointsToRedeem, orderTotal, orderId);

        balance.setPoints(account.getPoints());
        loyaltyBalanceRepository.save(balance);
        saveTransaction(account.getTransactions().get(0), customerId);

        return result;
    }

    @Transactional(readOnly = true)
    public int balance(UUID customerId) {
        return loyaltyBalanceRepository.findByCustomerId(customerId)
                .map(LoyaltyBalanceEntity::getPoints)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransaction> history(UUID customerId) {
        return loyaltyTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(e -> new LoyaltyTransaction(e.getPoints(), e.getType(), e.getOrderId(), e.getCreatedAt()))
                .toList();
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

    private LoyaltyBalanceEntity findOrCreateBalance(UUID customerId) {
        return loyaltyBalanceRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    LoyaltyBalanceEntity e = new LoyaltyBalanceEntity();
                    e.setCustomerId(customerId);
                    e.setPoints(0);
                    return e;
                });
    }

    private LoyaltyAccount toAccount(LoyaltyBalanceEntity balance) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setPoints(balance.getPoints());
        return account;
    }

    private void saveTransaction(LoyaltyTransaction tx, UUID customerId) {
        LoyaltyTransactionEntity entity = new LoyaltyTransactionEntity();
        entity.setCustomerId(customerId);
        entity.setPoints(tx.points());
        entity.setType(tx.type());
        entity.setOrderId(tx.orderId());
        entity.setCreatedAt(tx.createdAt());
        loyaltyTransactionRepository.save(entity);
    }

    private PromoCode loadPromo(String code) {
        PromoCodeEntity entity = promoCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(404, "Promo code not found"));
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
