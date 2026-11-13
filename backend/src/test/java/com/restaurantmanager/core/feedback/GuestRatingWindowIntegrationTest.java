package com.restaurantmanager.core.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A guest's phone remembers its own table orders and may offer to rate them for two hours after they are served,
 * even once the table has moved on to the next party. After that (e.g. the same customer back tomorrow) it may not.
 */
class GuestRatingWindowIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MenuItemEntity jollof;
    private UserEntity cashier;

    @BeforeEach
    void setUpData() {
        jollof = menuItem();
        cashier = createUser("Cashier", "+233300000301", "cashier-rating@x.com", "secret123", Role.CASHIER);
    }

    @Test
    void servedAndPaidOrder_canBeRatedFromThePhoneForTwoHours_thoughTheTablePageIsEmpty() throws Exception {
        RestaurantTableEntity table = table("R1");
        String token = table.getQrToken();
        UUID order = guestOrder(token);

        // Still cooking: the phone keeps it, nothing to rate yet
        mockMvc.perform(get(rating(token, order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateable").value(false))
                .andExpect(jsonPath("$.orderNumber").value(order.toString().substring(0, 8).toUpperCase()));

        serve(order);
        payCash(order);
        // The table's pages no longer show it (the sitting is over)...
        assertThat(json(mockMvc.perform(get("/orders/public/dine-in/tables/" + token + "/tracking"))
                .andReturn().getResponse().getContentAsString())).isEmpty();
        // ...but the phone that placed it can still rate it
        mockMvc.perform(get(rating(token, order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateable").value(true))
                .andExpect(jsonPath("$.rated").value(false))
                .andExpect(jsonPath("$.rateUntil").isNotEmpty());
        mockMvc.perform(post("/orders/public/dine-in/tables/" + token + "/orders/" + order + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Lovely\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get(rating(token, order))).andExpect(jsonPath("$.rated").value(true));
    }

    @Test
    void afterTwoHours_theOrderCanNoLongerBeRatedOrLookedUp() throws Exception {
        RestaurantTableEntity table = table("R2");
        String token = table.getQrToken();
        UUID order = guestOrder(token);
        serve(order);
        payCash(order);
        jdbcTemplate.update("UPDATE orders SET served_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(2 * 3600 + 60)), order);

        mockMvc.perform(get(rating(token, order))).andExpect(status().isGone());
        mockMvc.perform(post("/orders/public/dine-in/tables/" + token + "/orders/" + order + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isGone());
    }

    @Test
    void anotherTablesTokenOrACancelledOrder_isNotOffered() throws Exception {
        RestaurantTableEntity table = table("R3");
        RestaurantTableEntity other = table("R4");
        UUID order = guestOrder(table.getQrToken());
        mockMvc.perform(get(rating(other.getQrToken(), order))).andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/orders/" + order)
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed mind\"}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(rating(table.getQrToken(), order))).andExpect(status().isGone());
    }

    private static String rating(String token, UUID order) {
        return "/orders/public/dine-in/tables/" + token + "/orders/" + order + "/rating";
    }

    private void serve(UUID orderId) throws Exception {
        for (String next : new String[]{"CONFIRMED", "PREPARING", "READY", "COMPLETED"}) {
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

    private UUID guestOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/orders/public/dine-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + token + "\",\"guestPhone\":\"0244000009\","
                                + "\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
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

    private MenuItemEntity menuItem() {
        CategoryEntity category = new CategoryEntity();
        category.setName("Rating-" + Instant.now().toEpochMilli());
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity savedCategory = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(savedCategory);
        item.setName("Jollof");
        item.setPrice(new BigDecimal("40.00"));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}
