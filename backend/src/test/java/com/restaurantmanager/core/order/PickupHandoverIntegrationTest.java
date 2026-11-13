package com.restaurantmanager.core.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A READY pickup is only handed over (COMPLETED) with the customer's pickup code, or by a manager override. */
class PickupHandoverIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenNoCode_whenCashierMarksCollected_then400AndStillReady() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233281000001", "cash.pk1@x.com", "secret123", Role.CASHIER);
        ReadyPickup pickup = readyPickup("+233282000001", "cust.pk1@x.com");

        handOver(cashier, pickup.id, null).andExpect(status().isBadRequest());

        assertThat(orderRepository.findById(pickup.id).orElseThrow().getStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    void givenWrongCode_whenCashierMarksCollected_then409() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233281000002", "cash.pk2@x.com", "secret123", Role.CASHIER);
        ReadyPickup pickup = readyPickup("+233282000002", "cust.pk2@x.com");
        String wrong = pickup.code.equals("PK000000") ? "PK000001" : "PK000000";

        handOver(cashier, pickup.id, wrong).andExpect(status().isConflict());
    }

    @Test
    void givenCustomersCode_whenCashierMarksCollected_thenCompleted() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233281000003", "cash.pk3@x.com", "secret123", Role.CASHIER);
        ReadyPickup pickup = readyPickup("+233282000003", "cust.pk3@x.com");

        // Typed in lower case with a leading # still matches.
        handOver(cashier, pickup.id, "#" + pickup.code.toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void givenManager_whenMarksCollectedWithoutCode_thenOverrideAllowed() throws Exception {
        UserEntity manager = createUser("Manager", "+233281000004", "mgr.pk4@x.com", "secret123", Role.MANAGER);
        ReadyPickup pickup = readyPickup("+233282000004", "cust.pk4@x.com");

        handOver(manager, pickup.id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void givenDineInOrder_whenCompletedWithoutCode_thenUnaffected() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233281000005", "cash.pk5@x.com", "secret123", Role.CASHIER);
        UserEntity customer = createUser("Cust", "+233282000005", "cust.pk5@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity dish = createMenuItem("DineDish");
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"deliveryAddress\":\"1 Ring Rd\",\"items\":[{\"menuItemId\":\"" + dish.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        setStatus(id, OrderStatus.READY);

        handOver(cashier, id, null).andExpect(status().isOk());
    }

    private record ReadyPickup(UUID id, String code) {
    }

    private ReadyPickup readyPickup(String phone, String email) throws Exception {
        UserEntity customer = createUser("Cust", phone, email, "secret123", Role.CUSTOMER);
        MenuItemEntity dish = createMenuItem("PickupDish");
        JsonNode order = objectMapper.readTree(mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"pickupTime\":\"" + Instant.now().plusSeconds(1800)
                                + "\",\"items\":[{\"menuItemId\":\"" + dish.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(order.get("id").asText());
        setStatus(id, OrderStatus.READY); // paid and cooked: only the handover is under test here
        return new ReadyPickup(id, order.get("pickupCode").asText());
    }

    private void setStatus(UUID orderId, OrderStatus status) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        orderRepository.save(order);
    }

    private org.springframework.test.web.servlet.ResultActions handOver(UserEntity staff, UUID orderId, String code) throws Exception {
        String body = code == null
                ? "{\"status\":\"COMPLETED\"}"
                : "{\"status\":\"COMPLETED\",\"pickupCode\":\"" + code + "\"}";
        return mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MenuItemEntity createMenuItem(String name) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Pickup-" + name + "-" + System.nanoTime());
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity savedCategory = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(savedCategory);
        item.setName(name);
        item.setPrice(new BigDecimal("25.00"));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}
