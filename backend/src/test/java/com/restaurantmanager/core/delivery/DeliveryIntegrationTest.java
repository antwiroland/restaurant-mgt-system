package com.restaurantmanager.core.delivery;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private DeliveryZoneRepository zoneRepository;

    private UserEntity manager;
    private UserEntity customer;
    private UserEntity rider;
    private MenuItemEntity jollof;

    @BeforeEach
    void setUpData() {
        zoneRepository.deleteAll();
        manager = createUser("Manager", "+233280000001", "del-manager@x.com", "secret123", Role.MANAGER);
        customer = createUser("Efua", "+233280000002", "efua@x.com", "secret123", Role.CUSTOMER);
        rider = createUser("Yaw Rider", "+233280000003", "yaw@x.com", "secret123", Role.RIDER);
        jollof = createMenuItem("Jollof", "30.00");
    }

    @Test
    void givenZone_whenDeliveryOrderPlaced_thenFeeAddedAndEtaSet() throws Exception {
        UUID zoneId = createZone("East Legon", "12.50", "20.00");

        mockMvc.perform(get("/delivery/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("East Legon"));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryOrder(1, zoneId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(12.5))
                .andExpect(jsonPath("$.total").value(42.5))
                .andExpect(jsonPath("$.deliveryZoneId").value(zoneId.toString()))
                .andExpect(jsonPath("$.estimatedDeliveryTime").isNotEmpty());
    }

    @Test
    void givenZonesConfigured_whenDeliveryOrderWithoutZone_then400() throws Exception {
        createZone("Osu", "10", "0");
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryOrder(1, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenZoneMinimum_whenOrderBelowMinimum_then400() throws Exception {
        UUID zoneId = createZone("Tema", "15", "50.00");
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryOrder(1, zoneId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenAssignedRider_whenDeliveryFlowCompleted_thenStatusAndLocationTracked() throws Exception {
        UUID zoneId = createZone("Airport", "10", "0");
        UUID orderId = createDeliveryOrder(zoneId);

        mockMvc.perform(get("/delivery/riders")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Yaw Rider"));

        mockMvc.perform(post("/delivery/orders/" + orderId + "/assign")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"" + rider.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riderName").value("Yaw Rider"));

        mockMvc.perform(get("/delivery/rider/orders")
                        .header("Authorization", "Bearer " + accessToken(rider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // PENDING orders are not yet in the rider queue

        setStatus(orderId, OrderStatus.CONFIRMED);
        mockMvc.perform(get("/delivery/rider/orders")
                        .header("Authorization", "Bearer " + accessToken(rider)))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/delivery/orders/" + orderId + "/pickup")
                        .header("Authorization", "Bearer " + accessToken(rider)))
                .andExpect(status().isConflict()); // not READY yet

        setStatus(orderId, OrderStatus.READY);
        mockMvc.perform(post("/delivery/orders/" + orderId + "/pickup")
                        .header("Authorization", "Bearer " + accessToken(rider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));

        mockMvc.perform(post("/delivery/orders/" + orderId + "/location")
                        .header("Authorization", "Bearer " + accessToken(rider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":5.6037,\"longitude\":-0.1870}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/delivery/orders/" + orderId + "/tracking")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riderLatitude").value(5.6037))
                .andExpect(jsonPath("$.riderPhone").value("+233280000003"));

        mockMvc.perform(post("/delivery/orders/" + orderId + "/delivered")
                        .header("Authorization", "Bearer " + accessToken(rider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertNotNull(orderRepository.findById(orderId).orElseThrow().getDeliveredAt());
    }

    @Test
    void givenOtherRider_whenUpdatingDelivery_then403() throws Exception {
        UUID orderId = createDeliveryOrder(createZone("Spintex", "8", "0"));
        UserEntity otherRider = createUser("Kojo", "+233280000009", "kojo@x.com", "secret123", Role.RIDER);
        mockMvc.perform(post("/delivery/orders/" + orderId + "/assign")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"" + rider.getId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/delivery/orders/" + orderId + "/location")
                        .header("Authorization", "Bearer " + accessToken(otherRider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":5.6,\"longitude\":-0.18}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenNonRiderUser_whenAssigned_then400() throws Exception {
        UUID orderId = createDeliveryOrder(createZone("Labone", "8", "0"));
        mockMvc.perform(post("/delivery/orders/" + orderId + "/assign")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"" + customer.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenOtherCustomer_whenTrackingDelivery_then403() throws Exception {
        UUID orderId = createDeliveryOrder(createZone("Dansoman", "8", "0"));
        UserEntity stranger = createUser("Stranger", "+233280000010", "s@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(get("/delivery/orders/" + orderId + "/tracking")
                        .header("Authorization", "Bearer " + accessToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenRider_whenListingAllOrders_then403() throws Exception {
        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + accessToken(rider)))
                .andExpect(status().isForbidden());
    }

    private UUID createZone(String name, String fee, String minOrder) throws Exception {
        String body = mockMvc.perform(post("/delivery/zones")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"fee\":%s,\"minOrderAmount\":%s,\"estimatedMinutes\":40}".formatted(name, fee, minOrder)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createDeliveryOrder(UUID zoneId) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryOrder(1, zoneId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String deliveryOrder(int quantity, UUID zoneId) {
        return "{\"type\":\"DELIVERY\",\"deliveryAddress\":\"12 Oxford St, Osu\",\"items\":[{\"menuItemId\":\"" + jollof.getId()
                + "\",\"quantity\":" + quantity + "}]" + (zoneId == null ? "" : ",\"deliveryZoneId\":\"" + zoneId + "\"") + "}";
    }

    private void setStatus(UUID orderId, OrderStatus status) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        orderRepository.save(order);
        assertEquals(status, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Delivery-" + name + "-" + Instant.now().toEpochMilli());
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
