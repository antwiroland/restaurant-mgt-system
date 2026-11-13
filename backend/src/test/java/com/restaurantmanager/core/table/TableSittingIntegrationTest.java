package com.restaurantmanager.core.table;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The next party at a table never sees the previous party's orders, running bill or waiter calls. */
class TableSittingIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PaymentProps paymentProps;

    private MenuItemEntity jollof;
    private UserEntity cashier;
    private UserEntity manager;

    @BeforeEach
    void setUpData() {
        jollof = menuItem("Jollof", "40.00");
        cashier = createUser("Cashier", "+233300000201", "cashier-sitting@x.com", "secret123", Role.CASHIER);
        manager = createUser("Manager", "+233300000202", "manager-sitting@x.com", "secret123", Role.MANAGER);
    }

    @Test
    void finishedPartysOrdersBillAndRequests_areHiddenFromTheNextParty() throws Exception {
        RestaurantTableEntity table = table("S1");
        String token = table.getQrToken();

        // Party 1 orders, calls the waiter, eats, pays
        UUID first = guestOrder(token, "Ama");
        mockMvc.perform(post("/tables/public/" + token + "/requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CALL_WAITER\",\"note\":\"More water\"}")).andExpect(status().isCreated());
        assertThat(tracking(token)).hasSize(1);
        assertThat(bill(token).get("totalOrdered").decimalValue()).isEqualByComparingTo("40");
        assertThat(requests(token)).hasSize(1);

        serve(first);
        assertThat(tracking(token)).hasSize(1); // served but not paid: still their sitting
        payCash(first);

        // Table is free: a new party scanning the QR sees nothing from party 1
        assertThat(restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(tracking(token)).isEmpty();
        JsonNode emptyBill = bill(token);
        assertThat(emptyBill.get("activeOrderCount").asInt()).isZero();
        assertThat(emptyBill.get("totalOrdered").decimalValue()).isEqualByComparingTo("0");
        assertThat(emptyBill.get("totalPaid").decimalValue()).isEqualByComparingTo("0");
        assertThat(requests(token)).isEmpty();

        // Party 2 orders: they see only their own order and bill
        UUID second = guestOrder(token, "Kofi");
        JsonNode mine = tracking(token);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("orderId").asText()).isEqualTo(second.toString());
        assertThat(bill(token).get("totalOrdered").decimalValue()).isEqualByComparingTo("40");
        assertThat(bill(token).get("totalPaid").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void guestWhoPaidUpFront_keepsTrackingTheirOrderUntilItIsServed() throws Exception {
        RestaurantTableEntity table = table("S2");
        String token = table.getQrToken();
        UUID order = guestOrder(token, "Esi");
        String reference = json(mockMvc.perform(post("/orders/public/dine-in/tables/" + token + "/orders/" + order + "/pay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"momoPhone\":\"0244000001\",\"idempotencyKey\":\"s2\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("paystackReference").asText();
        succeed(reference);

        // Paid in full, so the table is free again, but the food is still cooking: the guest keeps their tracker
        assertThat(restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(tracking(token)).hasSize(1);

        serve(order);
        assertThat(tracking(token)).isEmpty();
    }

    @Test
    void staffClosingOrFreeingTheTable_endsTheSitting() throws Exception {
        RestaurantTableEntity table = table("S3");
        String token = table.getQrToken();
        UUID order = guestOrder(token, "Yaw");
        payCash(order); // paid, still with the kitchen
        assertThat(tracking(token)).hasSize(1);

        // Staff mark the table free for the next party
        mockMvc.perform(patch("/tables/" + table.getId() + "/status").header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        assertThat(tracking(token)).isEmpty();
        assertThat(bill(token).get("activeOrderCount").asInt()).isZero();
    }

    private void serve(UUID orderId) throws Exception {
        for (String next : new String[]{"CONFIRMED", "PREPARING", "READY", "COMPLETED"}) {
            if (next.equals("CONFIRMED") && !orderRepository.findById(orderId).orElseThrow().getStatus().name().equals("PENDING")) {
                continue;
            }
            mockMvc.perform(patch("/orders/" + orderId + "/status").header("Authorization", "Bearer " + accessToken(cashier))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    private void payCash(UUID orderId) throws Exception {
        mockMvc.perform(post("/payments/initiate").header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"CASH\",\"idempotencyKey\":\"cash-" + orderId + "\",\"amountTendered\":50}"))
                .andExpect(status().isCreated());
    }

    private UUID guestOrder(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/orders/public/dine-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + token + "\",\"guestName\":\"" + name + "\",\"guestPhone\":\"0244000009\","
                                + "\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json(body).get("id").asText());
    }

    private JsonNode tracking(String token) throws Exception {
        return json(mockMvc.perform(get("/orders/public/dine-in/tables/" + token + "/tracking"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode bill(String token) throws Exception {
        return json(mockMvc.perform(get("/orders/public/dine-in/tables/" + token + "/bill"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode requests(String token) throws Exception {
        return json(mockMvc.perform(get("/tables/public/" + token + "/requests"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void succeed(String reference) throws Exception {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-" + reference + "\",\"status\":\"success\",\"reference\":\"" + reference + "\"}}";
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(paymentProps.getPaystack().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post("/payments/webhook").header("X-Paystack-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private RestaurantTableEntity table(String number) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(number.toLowerCase() + "-" + UUID.randomUUID());
        return restaurantTableRepository.save(table);
    }

    private MenuItemEntity menuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Sitting-" + name + "-" + Instant.now().toEpochMilli());
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
