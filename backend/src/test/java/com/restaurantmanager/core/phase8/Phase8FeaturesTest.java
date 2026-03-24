package com.restaurantmanager.core.phase8;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.phase8.common.DiscountType;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyAccount;
import com.restaurantmanager.core.phase8.loyalty.LoyaltyService;
import com.restaurantmanager.core.phase8.offer.BuyXGetYOfferService;
import com.restaurantmanager.core.phase8.promo.PromoCode;
import com.restaurantmanager.core.phase8.promo.PromoService;
import com.restaurantmanager.core.phase8.qr.MobileSessionService;
import com.restaurantmanager.core.phase8.qr.TableQrPdfService;
import com.restaurantmanager.core.phase8.whatsapp.WhatsAppGateway;
import com.restaurantmanager.core.phase8.whatsapp.WhatsAppService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase8FeaturesTest {

    @Test
    void givenSuccessfulPayment_whenProcessed_thenWhatsAppOrderConfirmationSent() {
        AtomicReference<String> message = new AtomicReference<>();
        WhatsAppGateway gateway = (phone, text) -> message.set(text);
        WhatsAppService service = new WhatsAppService(gateway, ignored -> {});

        boolean sent = service.sendOrderConfirmation("+233200000001", "ORD-1");

        assertTrue(sent);
        assertTrue(message.get().contains("ORD-1"));
    }

    @Test
    void givenReceipt_whenGenerated_thenWhatsAppMessageContainsReceiptLinkAndReorderLink() {
        AtomicReference<String> message = new AtomicReference<>();
        WhatsAppService service = new WhatsAppService((phone, text) -> message.set(text), ignored -> {});

        service.sendReceipt("+233200000002", "https://app/receipt/1", "https://app/reorder/1");

        assertTrue(message.get().contains("receipt/1"));
        assertTrue(message.get().contains("reorder/1"));
    }

    @Test
    void givenOrderStatusChangedToReady_whenUpdated_thenWhatsAppStatusUpdateSent() {
        AtomicReference<String> message = new AtomicReference<>();
        WhatsAppService service = new WhatsAppService((phone, text) -> message.set(text), ignored -> {});

        service.sendStatusUpdate("+233200000003", "READY");

        assertTrue(message.get().contains("READY"));
    }

    @Test
    void givenWhatsAppApiDown_whenMessageFails_thenErrorLoggedAndOrderNotAffected() {
        List<String> errors = new ArrayList<>();
        WhatsAppService service = new WhatsAppService((phone, text) -> {
            throw new RuntimeException("network down");
        }, errors::add);

        boolean sent = service.sendOrderConfirmation("+233200000004", "ORD-4");

        assertFalse(sent);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("failed"));
    }

    @Test
    void givenTableQrCode_whenExportedAsPdf_thenPdfContainsCorrectTableNumber() {
        TableQrPdfService service = new TableQrPdfService();

        String content = new String(service.exportTableQrPdf("T9"), StandardCharsets.UTF_8);

        assertTrue(content.contains("TABLE:T9"));
    }

    @Test
    void givenMobileQrScan_whenTableLinked_thenStaffViewShowsTableHasActiveMobileSession() {
        MobileSessionService service = new MobileSessionService();

        service.linkSession("T2", "SESSION-2");

        assertTrue(service.hasActiveSession("T2"));
    }

    @Test
    void givenTableSessionClosed_whenStaffViews_thenActiveMobileSessionBadgeRemoved() {
        MobileSessionService service = new MobileSessionService();
        service.linkSession("T3", "SESSION-3");

        service.closeSession("T3");

        assertFalse(service.hasActiveSession("T3"));
    }

    @Test
    void givenPaymentSuccess_whenProcessed_thenPointsAccruedAtConfiguredRateForCustomer() {
        LoyaltyService service = new LoyaltyService(2);
        LoyaltyAccount account = new LoyaltyAccount();

        int points = service.accrue(account, new BigDecimal("50.00"), "ORD-50");

        assertEquals(100, points);
        assertEquals(100, account.getPoints());
    }

    @Test
    void givenCustomerWith200Points_whenRedeem100Points_thenOrderTotalReducedByEquivalentAmount() {
        LoyaltyService service = new LoyaltyService(1);
        LoyaltyAccount account = new LoyaltyAccount();
        account.setPoints(200);

        LoyaltyService.RedeemResult result = service.redeem(account, 100, new BigDecimal("25.00"), "ORD-51");

        assertEquals(new BigDecimal("24.00"), result.newTotal());
        assertEquals(100, result.remainingPoints());
    }

    @Test
    void givenRedeemMorePointsThanBalance_whenAttempted_then400() {
        LoyaltyService service = new LoyaltyService(1);
        LoyaltyAccount account = new LoyaltyAccount();
        account.setPoints(40);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.redeem(account, 100, new BigDecimal("10.00"), "ORD-52"));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenLoyaltyTransaction_whenViewHistory_thenEarnAndRedeemEntriesShown() {
        LoyaltyService service = new LoyaltyService(1);
        LoyaltyAccount account = new LoyaltyAccount();

        service.accrue(account, new BigDecimal("30.00"), "ORD-53");
        service.redeem(account, 10, new BigDecimal("5.00"), "ORD-53");

        assertEquals(2, service.history(account).size());
    }

    @Test
    void givenCustomerProfile_whenViewed_thenCurrentLoyaltyBalanceShown() {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setPoints(320);
        LoyaltyService service = new LoyaltyService(1);

        assertEquals(320, service.currentBalance(account));
    }

    @Test
    void givenValidPromoCode_whenApplied_thenCorrectDiscountAppliedToOrderTotal() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"), new BigDecimal("20"),
                Instant.now().plusSeconds(3600), 100, 0, true);

        BigDecimal discount = service.applyPromo(promo, new BigDecimal("100.00"), Instant.now());

        assertEquals(new BigDecimal("10.00"), discount);
    }

    @Test
    void givenPercentagePromo_whenApplied_thenDiscountIsPercentageOfSubtotal() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("PCT15", DiscountType.PERCENTAGE, new BigDecimal("15"), new BigDecimal("1"),
                Instant.now().plusSeconds(3600), 100, 0, true);

        BigDecimal discount = service.applyPromo(promo, new BigDecimal("200.00"), Instant.now());

        assertEquals(new BigDecimal("30.00"), discount);
    }

    @Test
    void givenFlatPromo_whenApplied_thenFixedAmountDeducted() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("FLAT5", DiscountType.FLAT, new BigDecimal("5.00"), new BigDecimal("1"),
                Instant.now().plusSeconds(3600), 100, 0, true);

        BigDecimal discount = service.applyPromo(promo, new BigDecimal("20.00"), Instant.now());

        assertEquals(new BigDecimal("5.00"), discount);
    }

    @Test
    void givenOrderBelowMinAmount_whenPromoApplied_then400MinimumNotMet() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("MIN50", DiscountType.FLAT, new BigDecimal("5.00"), new BigDecimal("50"),
                Instant.now().plusSeconds(3600), 100, 0, true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.applyPromo(promo, new BigDecimal("20.00"), Instant.now()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenExpiredPromoCode_whenApplied_then400Expired() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("OLD", DiscountType.FLAT, new BigDecimal("5.00"), new BigDecimal("1"),
                Instant.now().minusSeconds(1), 100, 0, true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.applyPromo(promo, new BigDecimal("20.00"), Instant.now()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenPromoAtUsageLimit_whenApplied_then400LimitReached() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("LIMIT", DiscountType.FLAT, new BigDecimal("5.00"), new BigDecimal("1"),
                Instant.now().plusSeconds(3600), 1, 1, true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.applyPromo(promo, new BigDecimal("20.00"), Instant.now()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenInactivePromoCode_whenApplied_then400Invalid() {
        PromoService service = new PromoService();
        PromoCode promo = new PromoCode("OFF", DiscountType.FLAT, new BigDecimal("5.00"), new BigDecimal("1"),
                Instant.now().plusSeconds(3600), 100, 0, false);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.applyPromo(promo, new BigDecimal("20.00"), Instant.now()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenBuyXGetYOffer_whenCustomerOrdersMeetsCriteria_thenFreeItemAddedToOrder() {
        BuyXGetYOfferService service = new BuyXGetYOfferService();

        int freeItems = service.freeItemsFor(4, 2, 1);

        assertEquals(2, freeItems);
    }

    @Test
    void givenBuyXGetYOffer_whenCriteriaNotMet_thenNoFreeItemAdded() {
        BuyXGetYOfferService service = new BuyXGetYOfferService();

        int freeItems = service.freeItemsFor(1, 2, 1);

        assertEquals(0, freeItems);
    }
}
