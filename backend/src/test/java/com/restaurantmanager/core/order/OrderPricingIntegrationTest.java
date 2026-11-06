package com.restaurantmanager.core.order;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.loyalty.LoyaltyBalanceEntity;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.promotion.DiscountType;
import com.restaurantmanager.core.promotion.PromoCodeEntity;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderPricingIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PaymentProps paymentProps;

    private UserEntity customer;
    private UserEntity manager;
    private MenuItemEntity pizza;

    @BeforeEach
    void setUpData() {
        customer = createUser("Ama", "+233250000001", "ama@x.com", "secret123", Role.CUSTOMER);
        manager = createUser("Kofi", "+233250000002", "kofi@x.com", "secret123", Role.MANAGER);
        pizza = createMenuItem("Pizza", "40.00");
    }

    @Test
    void givenValidPromoCode_whenOrderCreated_thenDiscountAppliedAndUsageConsumed() throws Exception {
        PromoCodeEntity promo = createPromo("SAVE10", DiscountType.PERCENTAGE, "10", 5);

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(2, "\"promoCode\":\"save10\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(80.0))
                .andExpect(jsonPath("$.promoCode").value("SAVE10"))
                .andExpect(jsonPath("$.promoDiscount").value(8.0))
                .andExpect(jsonPath("$.total").value(72.0));

        assertEquals(1, promoCodeRepository.findById(promo.getId()).orElseThrow().getUsageCount());
    }

    @Test
    void givenUnknownPromoCode_whenOrderCreated_then400AndNothingPersisted() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1, "\"promoCode\":\"NOPE\"")))
                .andExpect(status().isBadRequest());
        assertEquals(0, orderRepository.count());
    }

    @Test
    void givenExhaustedPromoCode_whenOrderCreated_then400() throws Exception {
        PromoCodeEntity promo = createPromo("ONCE", DiscountType.FLAT, "5", 1);
        promo.setUsageCount(1);
        promoCodeRepository.save(promo);

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1, "\"promoCode\":\"ONCE\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenPendingOrderWithPromo_whenCancelled_thenPromoUsageReleased() throws Exception {
        PromoCodeEntity promo = createPromo("FLAT5", DiscountType.FLAT, "5", 10);
        UUID orderId = createOrder(1, "\"promoCode\":\"FLAT5\"");
        assertEquals(1, promoCodeRepository.findById(promo.getId()).orElseThrow().getUsageCount());

        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().is2xxSuccessful());

        assertEquals(0, promoCodeRepository.findById(promo.getId()).orElseThrow().getUsageCount());
    }

    @Test
    void givenBuyTwoGetOneOffer_whenThreeOrdered_thenCheapestUnitFree() throws Exception {
        String offerBody = """
                {"name":"Pizza 2+1","menuItemId":"%s","buyQuantity":2,"freeQuantity":1}
                """.formatted(pizza.getId());
        mockMvc.perform(post("/promotions/offers")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running").value(true));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(3, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(120.0))
                .andExpect(jsonPath("$.offerDiscount").value(40.0))
                .andExpect(jsonPath("$.total").value(80.0));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(2, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offerDiscount").value(0.0));
    }

    @Test
    void givenCustomer_whenOfferListRequested_then403() throws Exception {
        mockMvc.perform(get("/promotions/offers")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenLoyaltyBalance_whenRedeemedAtCheckout_thenDiscountCappedAndPointsDeducted() throws Exception {
        seedPoints(customer.getId(), 5000); // worth 50 GHS at 0.01 / point

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1, "\"loyaltyPointsToRedeem\":5000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loyaltyDiscount").value(20.0))      // capped at 50% of 40
                .andExpect(jsonPath("$.loyaltyPointsRedeemed").value(2000))
                .andExpect(jsonPath("$.total").value(20.0));

        assertEquals(3000, loyaltyBalanceRepository.findByCustomerId(customer.getId()).orElseThrow().getPoints());
    }

    @Test
    void givenRedeemedOrder_whenCancelled_thenPointsRestored() throws Exception {
        seedPoints(customer.getId(), 1000);
        UUID orderId = createOrder(1, "\"loyaltyPointsToRedeem\":1000");
        assertEquals(0, loyaltyBalanceRepository.findByCustomerId(customer.getId()).orElseThrow().getPoints());

        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().is2xxSuccessful());

        assertEquals(1000, loyaltyBalanceRepository.findByCustomerId(customer.getId()).orElseThrow().getPoints());
    }

    @Test
    void givenCustomerOrder_whenPaymentSucceeds_thenLoyaltyPointsAwardedOnce() throws Exception {
        UUID orderId = createOrder(1, null);
        String initiated = mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"0244000000\",\"idempotencyKey\":\"loy-1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = objectMapper.readTree(initiated).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-loy-1\",\"status\":\"success\",\"reference\":\"" + reference + "\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("X-Paystack-Signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertEquals(40, loyaltyBalanceRepository.findByCustomerId(customer.getId()).orElseThrow().getPoints());
        assertEquals(40, orderRepository.findById(orderId).orElseThrow().getLoyaltyPointsAwarded());
        PaymentEntity payment = paymentRepository.findByPaystackReference(reference).orElseThrow();
        assertEquals(0, new BigDecimal("40.00").compareTo(payment.getAmount()));
    }

    @Test
    void givenStaffOrderWithoutCustomer_whenRedeemingPoints_then400() throws Exception {
        UserEntity cashier = createUser("Esi", "+233250000003", "esi@x.com", "secret123", Role.CASHIER);
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1, "\"loyaltyPointsToRedeem\":10")))
                .andExpect(status().isBadRequest());
    }

    private UUID createOrder(int quantity, String extra) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(quantity, extra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String orderBody(int quantity, String extra) {
        return "{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + pizza.getId() + "\",\"quantity\":" + quantity + "}]"
                + (extra == null ? "" : "," + extra) + "}";
    }

    private PromoCodeEntity createPromo(String code, DiscountType type, String value, int usageLimit) {
        PromoCodeEntity promo = new PromoCodeEntity();
        promo.setCode(code);
        promo.setDiscountType(type);
        promo.setDiscountValue(new BigDecimal(value));
        promo.setMinOrderAmount(BigDecimal.ZERO);
        promo.setExpiryDate(Instant.now().plusSeconds(86400));
        promo.setUsageLimit(usageLimit);
        promo.setUsageCount(0);
        promo.setActive(true);
        return promoCodeRepository.save(promo);
    }

    private void seedPoints(UUID customerId, int points) {
        LoyaltyBalanceEntity balance = new LoyaltyBalanceEntity();
        balance.setCustomerId(customerId);
        balance.setPoints(points);
        loyaltyBalanceRepository.save(balance);
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(paymentProps.getPaystack().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Pricing-" + name + "-" + Instant.now().toEpochMilli());
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity savedCategory = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(savedCategory);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}
