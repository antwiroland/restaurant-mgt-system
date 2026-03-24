package com.restaurantmanager.core.payment;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentPhase5IntegrationTest extends BaseIntegrationTest {
    @SpyBean
    private PaymentRealtimePublisher realtimePublisher;
    @Autowired
    private PaymentProps paymentProps;

    @BeforeEach
    void resetSpy() {
        clearInvocations(realtimePublisher);
    }

    @Test
    void givenValidOrder_whenInitiateMoMoPayment_then201WithPaystackReference() throws Exception {
        UserEntity customer = createUser("P1", "+233270010001", "p1@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P1Dish", "22.00"));

        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200000001\",\"idempotencyKey\":\"idem-p1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.paystackReference").value(containsString("PAY-")));
    }

    @Test
    void givenSameIdempotencyKey_whenInitiatePaymentTwice_thenSamePaymentReturnedNoDuplicate() throws Exception {
        UserEntity customer = createUser("P2", "+233270010002", "p2@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P2Dish", "24.00"));

        String first = mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200000002\",\"idempotencyKey\":\"idem-p2\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("paymentId").asText();

        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200000002\",\"idempotencyKey\":\"idem-p2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(firstId));
    }

    @Test
    void givenDifferentIdempotencyKey_whenInitiatePaymentForSameOrder_then201NewPayment() throws Exception {
        UserEntity customer = createUser("P3", "+233270010003", "p3@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P3Dish", "26.00"));
        String first = mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200000003\",\"idempotencyKey\":\"idem-p3-a\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("paymentId").asText();

        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200000003\",\"idempotencyKey\":\"idem-p3-b\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(not(firstId)));
    }

    @Test
    void givenNoPaystackKey_whenInitiatePayment_thenConfigurationErrorNotExposedToClient() throws Exception {
        UserEntity customer = createUser("P4", "+233270010004", "p4@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P4Dish", "28.00"));
        String prev = paymentProps.getPaystack().getSecretKey();
        paymentProps.getPaystack().setSecretKey(" ");
        try {
            mockMvc.perform(post("/payments/initiate")
                            .header("Authorization", "Bearer " + accessToken(customer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200000004\",\"idempotencyKey\":\"idem-p4\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value(not(containsString("paystack"))));
        } finally {
            paymentProps.getPaystack().setSecretKey(prev);
        }
    }

    @Test
    void givenValidPaystackSignature_whenSuccessWebhook_thenPaymentStatusSuccess() throws Exception {
        UserEntity customer = createUser("P5", "+233270010005", "p5@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P5Dish", "30.00"));
        String paymentJson = initiate(customer, orderId, "idem-p5");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p5\",\"reference\":\"" + reference + "\",\"status\":\"success\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments/" + paymentId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void givenValidPaystackSignature_whenSuccessWebhook_thenOrderStatusChangedToConfirmed() throws Exception {
        UserEntity customer = createUser("P6", "+233270010006", "p6@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P6Dish", "32.00"));
        String paymentJson = initiate(customer, orderId, "idem-p6");
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p6\",\"reference\":\"" + reference + "\",\"status\":\"success\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void givenValidPaystackSignature_whenFailedWebhook_thenPaymentStatusFailed() throws Exception {
        UserEntity customer = createUser("P7", "+233270010007", "p7@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P7Dish", "34.00"));
        String paymentJson = initiate(customer, orderId, "idem-p7");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.failed\",\"data\":{\"id\":\"evt-p7\",\"reference\":\"" + reference + "\",\"status\":\"failed\",\"gateway_response\":\"Insufficient funds\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments/" + paymentId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void givenInvalidSignature_whenWebhookReceived_then400Rejected() throws Exception {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p8\",\"reference\":\"PAY-XX\",\"status\":\"success\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenMissingSignatureHeader_whenWebhookReceived_then400Rejected() throws Exception {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p9\",\"reference\":\"PAY-YY\",\"status\":\"success\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenDuplicateWebhookEvent_whenReceivedTwice_thenProcessedOnlyOnce() throws Exception {
        UserEntity customer = createUser("P10", "+233270010010", "p10@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P10Dish", "31.00"));
        String paymentJson = initiate(customer, orderId, "idem-p10");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-dup\",\"reference\":\"" + reference + "\",\"status\":\"success\"}}";
        String sig = signature(payload);
        mockMvc.perform(post("/payments/webhook").header("x-paystack-signature", sig).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post("/payments/webhook").header("x-paystack-signature", sig).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(realtimePublisher, times(1)).publishPaymentStatusChanged(any(UUID.class), any(UUID.class),
                any(PaymentStatus.class), eq(PaymentStatus.SUCCESS), any(BigDecimal.class), eq(PaymentMethod.MOBILE_MONEY));
    }

    @Test
    void givenInitiatedPayment_whenPolled_thenStatusIsInitiated() throws Exception {
        UserEntity customer = createUser("P11", "+233270010011", "p11@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P11Dish", "25.00"));
        String paymentJson = initiate(customer, orderId, "idem-p11");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();

        mockMvc.perform(get("/payments/" + paymentId + "/verify")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    void givenSuccessfulPayment_whenGetPayment_thenStatusIsSuccessWithPaidAtTimestamp() throws Exception {
        UserEntity customer = createUser("P12", "+233270010012", "p12@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P12Dish", "27.00"));
        String paymentJson = initiate(customer, orderId, "idem-p12");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p12\",\"reference\":\"" + reference + "\",\"status\":\"success\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments/" + paymentId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void givenFailedPayment_whenRetried_then201NewPaymentWithNewReference() throws Exception {
        UserEntity customer = createUser("P13", "+233270010013", "p13@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P13Dish", "29.00"));
        String paymentJson = initiate(customer, orderId, "idem-p13");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String failPayload = "{\"event\":\"charge.failed\",\"data\":{\"id\":\"evt-p13\",\"reference\":\"" + reference + "\",\"status\":\"failed\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(failPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failPayload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/payments/" + paymentId + "/retry")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"momoPhone\":\"+233200000013\",\"idempotencyKey\":\"idem-p13-retry\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paystackReference").value(not(reference)));
    }

    @Test
    void givenSuccessfulPayment_whenGetReceipt_then200WithCorrectOrderTotalsAndPaymentMethod() throws Exception {
        UserEntity customer = createUser("P14", "+233270010014", "p14@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P14Dish", "33.00"));
        String paymentJson = initiate(customer, orderId, "idem-p14");
        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();

        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p14\",\"reference\":\"" + reference + "\",\"status\":\"success\"}}";
        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments/" + paymentId + "/receipt")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.paymentMethod").value("MOBILE_MONEY"))
                .andExpect(jsonPath("$.total").value(33.0));
    }

    @Test
    void givenNoPayment_whenGetReceipt_then404() throws Exception {
        UserEntity customer = createUser("P15", "+233270010015", "p15@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P15Dish", "20.00"));

        mockMvc.perform(get("/payments/orders/" + orderId + "/receipt")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenSubscribedToPaymentTopic_whenWebhookSuccess_thenWebSocketReceivesPaymentStatusChangedEvent() throws Exception {
        UserEntity customer = createUser("P16", "+233270010016", "p16@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P16Dish", "18.00"));
        String paymentJson = initiate(customer, orderId, "idem-p16");
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-p16\",\"reference\":\"" + reference + "\",\"status\":\"success\"}}";

        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(realtimePublisher, atLeastOnce()).publishPaymentStatusChanged(any(UUID.class), any(UUID.class),
                any(PaymentStatus.class), eq(PaymentStatus.SUCCESS), any(BigDecimal.class), eq(PaymentMethod.MOBILE_MONEY));
    }

    @Test
    void givenSubscribedToPaymentTopic_whenWebhookFailed_thenWebSocketReceivesPaymentFailedEvent() throws Exception {
        UserEntity customer = createUser("P17", "+233270010017", "p17@x.com", "secret123", Role.CUSTOMER);
        UUID orderId = createPickupOrder(customer, createMenuItem("P17Dish", "19.00"));
        String paymentJson = initiate(customer, orderId, "idem-p17");
        String reference = objectMapper.readTree(paymentJson).get("paystackReference").asText();
        String payload = "{\"event\":\"charge.failed\",\"data\":{\"id\":\"evt-p17\",\"reference\":\"" + reference + "\",\"status\":\"failed\",\"gateway_response\":\"Declined\"}}";

        mockMvc.perform(post("/payments/webhook")
                        .header("x-paystack-signature", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(realtimePublisher, atLeastOnce()).publishPaymentFailed(any(UUID.class), any(UUID.class), contains("Declined"));
    }

    private String initiate(UserEntity customer, UUID orderId, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"+233200999999\",\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(paymentProps.getPaystack().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private UUID createPickupOrder(UserEntity customer, MenuItemEntity item) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"pickupTime\":\"" + Instant.now().plusSeconds(1500) + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Phase5-" + name + "-" + Instant.now().toEpochMilli());
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
