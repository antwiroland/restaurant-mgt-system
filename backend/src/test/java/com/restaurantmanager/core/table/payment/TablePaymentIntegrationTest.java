package com.restaurantmanager.core.table.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff-side payment safety for dine-in: the watch list, at-risk tables, walkouts and the walkout report. */
class TablePaymentIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MenuItemEntity jollof;
    private UserEntity cashier;
    private UserEntity manager;

    @BeforeEach
    void setUpData() {
        jollof = menuItem("Jollof", "40.00");
        cashier = createUser("Esi Waiter", "+233300000201", "esi@x.com", "secret123", Role.CASHIER);
        manager = createUser("Kojo Manager", "+233300000202", "kojo@x.com", "secret123", Role.MANAGER);
    }

    @Test
    void watch_listsTablesThatOwe_andFlagsServedUnpaidAndBillWaiting() throws Exception {
        RestaurantTableEntity served = table("W1");
        RestaurantTableEntity billAsked = table("W2");
        RestaurantTableEntity eating = table("W3");
        UUID servedOrder = guestOrder(served, "0244000001");
        guestOrder(billAsked, "0244000002");
        guestOrder(eating, "0244000003");
        serve(servedOrder);

        JsonNode watch = watch(cashier);
        assertThat(watch).hasSize(3);
        assertThat(entry(watch, "W1").get("allServed").asBoolean()).isTrue();
        assertThat(entry(watch, "W1").get("risk").asText()).isEqualTo("NONE"); // just served
        assertThat(entry(watch, "W1").get("outstanding").decimalValue()).isEqualByComparingTo("40.00");

        // 25 minutes after serving with nothing paid: staff should go over
        jdbcTemplate.update("UPDATE orders SET served_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(25 * 60)), servedOrder);
        // the bill was asked for 12 minutes ago and is still unpaid
        mockMvc.perform(post("/tables/public/" + billAsked.getQrToken() + "/requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BILL\"}")).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE service_requests SET created_at = ?", Timestamp.from(Instant.now().minusSeconds(12 * 60)));

        watch = watch(cashier);
        assertThat(entry(watch, "W1").get("risk").asText()).isEqualTo("SERVED_UNPAID");
        assertThat(entry(watch, "W1").get("minutesWaiting").asLong()).isGreaterThanOrEqualTo(25);
        assertThat(entry(watch, "W2").get("risk").asText()).isEqualTo("BILL_WAITING");
        assertThat(entry(watch, "W3").get("risk").asText()).isEqualTo("NONE"); // still eating
        assertThat(watch.get(2).get("tableNumber").asText()).isEqualTo("W3"); // at-risk tables first

        mockMvc.perform(get("/tables/payment-watch").header("Authorization", "Bearer "
                        + accessToken(createUser("Rider", "+233300000203", "r@x.com", "secret123", Role.RIDER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void walkout_writesOffTheTable_freesIt_andIsReported() throws Exception {
        RestaurantTableEntity table = table("W9");
        UUID qrOrder = guestOrder(table, "0244000009");
        serve(qrOrder);
        UUID posOrder = UUID.fromString(json(mockMvc.perform(post("/orders").header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());

        String path = "/tables/" + table.getId() + "/walkout";
        mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WALKED_OUT\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}")).andExpect(status().isBadRequest());
        // the table cannot be closed while it owes money
        mockMvc.perform(post("/orders/dine-in/tables/" + table.getId() + "/close").header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isConflict());

        JsonNode walkout = json(mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WALKED_OUT\",\"note\":\"Left by the side door\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(walkout.get("amountOwed").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(walkout.get("orderCount").asInt()).isEqualTo(2);
        assertThat(walkout.get("orderTakenBy").asText()).isEqualTo("QR (guest), Esi Waiter");
        assertThat(walkout.get("guestPhones").asText()).isEqualTo("0244000009");
        assertThat(walkout.get("recordedByName").asText()).isEqualTo("Kojo Manager");

        assertThat(restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(TableStatus.AVAILABLE);
        OrderEntity pos = orderRepository.findById(posOrder).orElseThrow();
        assertThat(pos.isWrittenOff()).isTrue();
        assertThat(pos.getStatus()).isEqualTo(OrderStatus.COMPLETED); // off the kitchen screen
        assertThat(watch(manager)).isEmpty();
        mockMvc.perform(get("/orders/public/dine-in/tables/" + table.getQrToken() + "/bill"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.outstandingTotal").value(0));
        mockMvc.perform(post("/orders/dine-in/tables/" + table.getId() + "/close").header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(path).header("Authorization", "Bearer " + accessToken(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WALKED_OUT\"}")).andExpect(status().isConflict());
        assertThat(auditLogRepository.findAll()).anyMatch(a -> a.getAction() == AuditAction.WALKOUT_RECORDED);

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Accra"));
        MockHttpServletResponse csv = mockMvc.perform(get("/reports/walkouts?from=" + today.minusDays(1) + "&to=" + today)
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String text = new String(csv.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).contains("W9", "80.00", "Walked out", "Left by the side door", "\"QR (guest), Esi Waiter\"", "Kojo Manager");
        mockMvc.perform(get("/reports/walkouts?format=PDF&from=" + today + "&to=" + today).header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"));
        mockMvc.perform(get("/reports/walkouts?from=" + today + "&to=" + today).header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    private void serve(UUID orderId) throws Exception {
        for (String next : new String[]{"CONFIRMED", "PREPARING", "READY", "COMPLETED"}) {
            mockMvc.perform(patch("/orders/" + orderId + "/status").header("Authorization", "Bearer " + accessToken(cashier))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(orderRepository.findById(orderId).orElseThrow().getServedAt()).isNotNull();
    }

    private JsonNode watch(UserEntity user) throws Exception {
        return json(mockMvc.perform(get("/tables/payment-watch").header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode entry(JsonNode watch, String tableNumber) {
        for (JsonNode row : watch) {
            if (row.get("tableNumber").asText().equals(tableNumber)) return row;
        }
        throw new AssertionError("No watch entry for " + tableNumber + ": " + watch);
    }

    private UUID guestOrder(RestaurantTableEntity table, String phone) throws Exception {
        String body = mockMvc.perform(post("/orders/public/dine-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + table.getQrToken() + "\",\"items\":[{\"menuItemId\":\"" + jollof.getId()
                                + "\",\"quantity\":1}],\"guestPhone\":\"" + phone + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json(body).get("id").asText());
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
        category.setName("Watch-" + name + "-" + Instant.now().toEpochMilli());
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
