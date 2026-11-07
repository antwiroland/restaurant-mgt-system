package com.restaurantmanager.core.feedback;

import com.restaurantmanager.core.BaseIntegrationTest;
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
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackIntegrationTest extends BaseIntegrationTest {
    private UserEntity customer;
    private UserEntity manager;
    private MenuItemEntity item;

    @BeforeEach
    void setUpData() {
        customer = createUser("Yaa", "+233330000001", "yaa@x.com", "secret123", Role.CUSTOMER);
        manager = createUser("Manager", "+233330000002", "fb-manager@x.com", "secret123", Role.MANAGER);
        item = createMenuItem("Kenkey", "25.00");
    }

    @Test
    void givenCompletedOrder_whenCustomerRates_thenStoredSummarisedAndManagerCanReply() throws Exception {
        UUID orderId = completedOrder();

        String body = mockMvc.perform(post("/orders/" + orderId + "/feedback")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":2,\"foodRating\":3,\"serviceRating\":1,\"comment\":\"Waited 40 minutes\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Yaa"))
                .andReturn().getResponse().getContentAsString();
        String feedbackId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(get("/feedback/summary")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.averageRating").value(2.0))
                .andExpect(jsonPath("$.lowRatings").value(1))
                .andExpect(jsonPath("$.distribution.2").value(1));

        mockMvc.perform(get("/feedback?maxRating=2")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(jsonPath("$[0].comment").value("Waited 40 minutes"));

        mockMvc.perform(post("/feedback/" + feedbackId + "/respond")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"Sorry about the wait, your next delivery is on us.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
    }

    @Test
    void givenOrderNotFinished_whenRated_then409() throws Exception {
        UUID orderId = createOrder();
        mockMvc.perform(post("/orders/" + orderId + "/feedback")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenRatedOrder_whenRatedAgainOrBySomeoneElse_thenRejected() throws Exception {
        UUID orderId = completedOrder();
        mockMvc.perform(post("/orders/" + orderId + "/feedback")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/orders/" + orderId + "/feedback")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isConflict());

        UserEntity other = createUser("Other", "+233330000009", "o9@x.com", "secret123", Role.CUSTOMER);
        UUID otherOrder = completedOrder();
        mockMvc.perform(post("/orders/" + otherOrder + "/feedback")
                        .header("Authorization", "Bearer " + accessToken(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenGuestTableOrder_whenRatedWithTableToken_thenAccepted() throws Exception {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber("FB1");
        table.setCapacity(2);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken("fb1-" + Instant.now().toEpochMilli());
        table = restaurantTableRepository.save(table);
        String body = mockMvc.perform(post("/orders/public/dine-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + table.getQrToken() + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        complete(orderId);

        mockMvc.perform(post("/orders/public/dine-in/tables/wrong-token/orders/" + orderId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/orders/public/dine-in/tables/" + table.getQrToken() + "/orders/" + orderId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"guestName\":\"Table guest\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Table guest"));
    }

    private UUID completedOrder() throws Exception {
        UUID orderId = createOrder();
        complete(orderId);
        return orderId;
    }

    private void complete(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
    }

    private UUID createOrder() throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Fb-" + name + "-" + Instant.now().toEpochMilli());
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity savedCategory = categoryRepository.save(category);
        MenuItemEntity menuItem = new MenuItemEntity();
        menuItem.setCategory(savedCategory);
        menuItem.setName(name);
        menuItem.setPrice(new BigDecimal(price));
        menuItem.setAvailable(true);
        menuItem.setActive(true);
        return menuItemRepository.save(menuItem);
    }
}
