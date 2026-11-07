package com.restaurantmanager.core.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.split.SplitAllocator;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SplitPaymentIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PaymentProps paymentProps;

    private UserEntity host;
    private UserEntity friend;
    private MenuItemEntity waakye;
    private MenuItemEntity kelewele;

    @BeforeEach
    void setUpData() {
        host = createUser("Akosua", "+233300000001", "akosua@x.com", "secret123", Role.CUSTOMER);
        friend = createUser("Kwesi", "+233300000002", "kwesi@x.com", "secret123", Role.CUSTOMER);
        waakye = createMenuItem("Waakye", "30.00");
        kelewele = createMenuItem("Kelewele", "10.00");
    }

    @Test
    void allocator_sharesAlwaysAddUpToTotal() {
        List<BigDecimal> shares = SplitAllocator.allocate(new BigDecimal("100.00"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(List.of(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33")), shares);
        assertEquals(new BigDecimal("100.00"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void givenGroupOrder_whenSplitByItemsAndEachPaysOwnShare_thenOrderConfirmedOnlyWhenAllPaid() throws Exception {
        UUID orderId = groupOrder();

        String splitsBody = mockMvc.perform(post("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BY_ITEMS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode splits = objectMapper.readTree(splitsBody);
        JsonNode hostSplit = find(splits, host.getId());
        JsonNode friendSplit = find(splits, friend.getId());
        assertEquals(30.0, hostSplit.get("amount").asDouble());
        assertEquals(20.0, friendSplit.get("amount").asDouble());

        // Paying the whole bill is not allowed once it is split
        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiate(orderId, null, "full-1")))
                .andExpect(status().isConflict());

        // The friend can see and pay their own share even though they did not place the order
        mockMvc.perform(get("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(friend)))
                .andExpect(status().isOk());
        String friendRef = pay(friend, orderId, friendSplit.get("id").asText(), "friend-1", 20.0);
        succeed(friendRef);
        assertEquals(OrderStatus.PENDING, orderRepository.findById(orderId).orElseThrow().getStatus());

        String hostRef = pay(host, orderId, hostSplit.get("id").asText(), "host-1", 30.0);
        succeed(hostRef);
        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(orderId).orElseThrow().getStatus());

        mockMvc.perform(get("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(host)))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[1].status").value("PAID"));
    }

    @Test
    void givenPaidShare_whenPayingSameShareAgainOrResplitting_thenRejected() throws Exception {
        UUID orderId = groupOrder();
        JsonNode splits = objectMapper.readTree(mockMvc.perform(post("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EQUAL\"}"))
                .andExpect(jsonPath("$[0].amount").value(25.0))
                .andReturn().getResponse().getContentAsString());
        String splitId = find(splits, friend.getId()).get("id").asText();
        succeed(pay(friend, orderId, splitId, "eq-1", 25.0));

        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(friend))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiate(orderId, splitId, "eq-2")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BY_ITEMS\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenGroupParticipant_whenViewingGroupOrder_thenAllowedToViewAndListButNotCancel() throws Exception {
        UUID orderId = groupOrder();
        UserEntity stranger = createUser("Outsider", "+233300000008", "s8@x.com", "secret123", Role.CUSTOMER);

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + accessToken(friend)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + accessToken(friend)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(orderId)).isNotEmpty());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(friend)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + accessToken(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + accessToken(stranger)))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(orderId)).isEmpty());
    }

    @Test
    void givenStranger_whenPayingSomeoneElsesShare_then403() throws Exception {
        UUID orderId = groupOrder();
        JsonNode splits = objectMapper.readTree(mockMvc.perform(post("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BY_ITEMS\"}"))
                .andReturn().getResponse().getContentAsString());
        UserEntity stranger = createUser("Stranger", "+233300000009", "s9@x.com", "secret123", Role.CUSTOMER);

        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiate(orderId, find(splits, friend.getId()).get("id").asText(), "x-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenStaffPickupOrder_whenSplitEquallyBetweenNamedGuests_thenSharesCreated() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233300000005", "split-cashier@x.com", "secret123", Role.CASHIER);
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + waakye.getId() + "\",\"quantity\":1},{\"menuItemId\":\"" + kelewele.getId() + "\",\"quantity\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(post("/orders/" + orderId + "/splits")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EQUAL\",\"names\":[\"Ama\",\"Kofi\",\"Esi\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ama"))
                .andExpect(jsonPath("$[0].amount").value(13.34))
                .andExpect(jsonPath("$[2].amount").value(13.33));
    }

    @Test
    void givenGuestTableOrder_whenGuestsSplitEquallyAndPayShares_thenOrderConfirmedWhenAllPaid() throws Exception {
        RestaurantTableEntity table = guestTable("SP1");
        UUID orderId = guestOrder(table);
        String base = "/orders/public/dine-in/tables/" + table.getQrToken() + "/orders/" + orderId;

        // Wrong table token cannot see or split the order
        mockMvc.perform(get("/orders/public/dine-in/tables/wrong-token/orders/" + orderId + "/splits"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(base + "/splits").contentType(MediaType.APPLICATION_JSON).content("{\"parts\":1}"))
                .andExpect(status().isBadRequest());

        JsonNode splits = objectMapper.readTree(mockMvc.perform(post(base + "/splits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":2,\"names\":[\"Ama\",\"Kofi\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ama"))
                .andExpect(jsonPath("$[0].amount").value(20.0))
                .andReturn().getResponse().getContentAsString());
        String amaSplit = splits.get(0).get("id").asText();
        String kofiSplit = splits.get(1).get("id").asText();

        String amaRef = guestPay(base, amaSplit, "guest-ama-1");
        // Same share cannot be started twice while in progress; same key returns the same payment
        mockMvc.perform(post(base + "/splits/" + amaSplit + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"momoPhone\":\"0244000001\",\"idempotencyKey\":\"guest-ama-2\"}"))
                .andExpect(status().isConflict());
        assertEquals(amaRef, guestPay(base, amaSplit, "guest-ama-1"));

        // Re-splitting is blocked once a payment has started
        mockMvc.perform(post(base + "/splits").contentType(MediaType.APPLICATION_JSON).content("{\"parts\":3}"))
                .andExpect(status().isConflict());

        succeed(amaRef);
        assertEquals(OrderStatus.PENDING, orderRepository.findById(orderId).orElseThrow().getStatus());
        succeed(guestPay(base, kofiSplit, "guest-kofi-1"));
        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(orderId).orElseThrow().getStatus());

        mockMvc.perform(get(base + "/splits"))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[1].status").value("PAID"));

        String paymentId = paymentRepository.findByPaystackReference(amaRef).orElseThrow().getId().toString();
        mockMvc.perform(post(base + "/payments/" + paymentId + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void givenOrderFromAnotherTable_whenGuestPaysWithOwnTableToken_then404() throws Exception {
        RestaurantTableEntity mine = guestTable("SP2");
        RestaurantTableEntity other = guestTable("SP3");
        UUID otherOrder = guestOrder(other);
        String otherBase = "/orders/public/dine-in/tables/" + other.getQrToken() + "/orders/" + otherOrder;
        String split = objectMapper.readTree(mockMvc.perform(post(otherBase + "/splits")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parts\":2}"))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();

        mockMvc.perform(post("/orders/public/dine-in/tables/" + mine.getQrToken() + "/orders/" + otherOrder + "/splits/" + split + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"momoPhone\":\"0244000001\",\"idempotencyKey\":\"cross-1\"}"))
                .andExpect(status().isNotFound());
    }

    private RestaurantTableEntity guestTable(String number) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(number.toLowerCase() + "-" + UUID.randomUUID());
        return restaurantTableRepository.save(table);
    }

    private UUID guestOrder(RestaurantTableEntity table) throws Exception {
        String body = mockMvc.perform(post("/orders/public/dine-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + table.getQrToken() + "\",\"items\":[{\"menuItemId\":\"" + waakye.getId()
                                + "\",\"quantity\":1},{\"menuItemId\":\"" + kelewele.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(40.0))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String guestPay(String base, String splitId, String key) throws Exception {
        String body = mockMvc.perform(post(base + "/splits/" + splitId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"momoPhone\":\"0244000001\",\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("paystackReference").asText();
    }

    private UUID groupOrder() throws Exception {
        JsonNode session = objectMapper.readTree(mockMvc.perform(post("/orders/group/sessions")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Akosua\"}"))
                .andReturn().getResponse().getContentAsString());
        String code = session.get("sessionCode").asText();
        String hostParticipant = session.get("hostParticipantId").asText();
        String friendParticipant = objectMapper.readTree(mockMvc.perform(post("/orders/group/sessions/" + code + "/join")
                        .header("Authorization", "Bearer " + accessToken(friend))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Kwesi\"}"))
                .andReturn().getResponse().getContentAsString()).get("participantId").asText();

        addItems(host, code, hostParticipant, waakye.getId(), 1);
        addItems(friend, code, friendParticipant, kelewele.getId(), 2);

        String order = mockMvc.perform(post("/orders/group/sessions/" + code + "/finalize")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\"}"))
                .andExpect(jsonPath("$.total").value(50.0))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(order).get("id").asText());
    }

    private void addItems(UserEntity user, String code, String participantId, UUID menuItemId, int quantity) throws Exception {
        mockMvc.perform(post("/orders/group/sessions/" + code + "/items")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + participantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().is2xxSuccessful());
    }

    private String pay(UserEntity user, UUID orderId, String splitId, String key, double expectedAmount) throws Exception {
        String body = mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiate(orderId, splitId, key)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = objectMapper.readTree(body).get("paystackReference").asText();
        PaymentEntity payment = paymentRepository.findByPaystackReference(reference).orElseThrow();
        assertEquals(expectedAmount, payment.getAmount().doubleValue());
        return reference;
    }

    private String initiate(UUID orderId, String splitId, String key) {
        return "{\"orderId\":\"" + orderId + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"0244000000\",\"idempotencyKey\":\"" + key + "\""
                + (splitId == null ? "" : ",\"splitId\":\"" + splitId + "\"") + "}";
    }

    private void succeed(String reference) throws Exception {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-" + reference + "\",\"status\":\"success\",\"reference\":\"" + reference + "\"}}";
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(paymentProps.getPaystack().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post("/payments/webhook")
                        .header("X-Paystack-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private JsonNode find(JsonNode splits, UUID userId) {
        for (JsonNode split : splits) {
            if (userId.toString().equals(split.path("userId").asText())) {
                return split;
            }
        }
        throw new AssertionError("No split for user " + userId);
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Split-" + name + "-" + Instant.now().toEpochMilli());
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
