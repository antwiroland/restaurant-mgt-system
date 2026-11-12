package com.restaurantmanager.core.order;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Orders placed by customers in the mobile app used to have no branch, so staff
 * assigned to a branch could not see or process them. Every order now gets a
 * branch where one can be determined, and unassigned orders are a shared queue.
 */
class OrderBranchAssignmentIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private BranchRepository branchRepository;

    private BranchEntity osu;
    private BranchEntity legon;
    private UserEntity customer;
    private UserEntity manager;
    private String osuCashierToken;
    private String legonCashierToken;
    private MenuItemEntity dish;

    @BeforeEach
    void setUp() {
        // Unique codes: branches are not cleaned between tests, so never rely on "the only branch".
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        osu = branch("OSU-" + suffix, "Osu");
        legon = branch("ELG-" + suffix, "East Legon");
        customer = createUser("App Customer", "+233249" + Math.abs(suffix.hashCode() % 100000), null, "secret123", Role.CUSTOMER);
        manager = createUser("Manager", "+233208" + Math.abs(suffix.hashCode() % 100000), null, "secret123", Role.MANAGER);
        osuCashierToken = cashierToken("+233207" + Math.abs((suffix + "a").hashCode() % 100000), osu);
        legonCashierToken = cashierToken("+233206" + Math.abs((suffix + "b").hashCode() % 100000), legon);
        dish = menuItem("Jollof " + suffix, "80.00");
    }

    @Test
    void givenCustomerDeliveryOrder_whenZoneBelongsToBranch_thenOrderTakesZoneBranchAndThatBranchCanProcessIt() throws Exception {
        UUID zoneId = zone("Osu & Labone", osu.getId());
        UUID orderId = placeOrder("{\"type\":\"DELIVERY\",\"deliveryAddress\":\"12 Oxford St\",\"deliveryZoneId\":\"%s\",\"items\":[%s]}"
                .formatted(zoneId, item()), osu.getId());

        assertEquals(osu.getId(), orderRepository.findById(orderId).orElseThrow().getBranch().getId());
        updateStatus(orderId, osuCashierToken, 200);
        updateStatus(orderId, legonCashierToken, 403);
    }

    @Test
    void givenCustomerPickupOrder_whenBranchRequested_thenOrderGoesToThatBranch() throws Exception {
        UUID orderId = placeOrder("{\"type\":\"PICKUP\",\"branchId\":\"%s\",\"items\":[%s]}".formatted(legon.getId(), item()), legon.getId());

        assertEquals(legon.getId(), orderRepository.findById(orderId).orElseThrow().getBranch().getId());
        // pickup reaches the kitchen once paid
        mockMvc.perform(get("/kds/board").header("Authorization", "Bearer " + legonCashierToken))
                .andExpect(jsonPath("$.columns.PENDING[?(@.orderId == '%s')]".formatted(orderId)).doesNotExist());
        payCash(orderId, legonCashierToken);
        mockMvc.perform(get("/kds/board").header("Authorization", "Bearer " + legonCashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.CONFIRMED[?(@.orderId == '%s')]".formatted(orderId)).exists());
        mockMvc.perform(get("/kds/board").header("Authorization", "Bearer " + osuCashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.CONFIRMED[?(@.orderId == '%s')]".formatted(orderId)).doesNotExist());
    }

    @Test
    void givenInactiveBranchRequested_whenOrderPlaced_then400() throws Exception {
        legon.setActive(false);
        branchRepository.save(legon);
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"branchId\":\"%s\",\"items\":[%s]}".formatted(legon.getId(), item())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenUnassignedOrder_whenBranchScopedStaffWork_thenSharedQueueIsVisibleAndActionable() throws Exception {
        // Several active branches and no default configured: the order cannot be assigned.
        UUID orderId = placeOrder("{\"type\":\"PICKUP\",\"items\":[%s]}".formatted(item()), null);
        assertNull(orderRepository.findById(orderId).orElseThrow().getBranch());

        String list = mockMvc.perform(get("/orders").header("Authorization", "Bearer " + osuCashierToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(list.contains(orderId.toString()), "unassigned order should be in a branch cashier's order list");
        updateStatus(orderId, legonCashierToken, 409); // unpaid pickup cannot be sent to the kitchen
        payCash(orderId, legonCashierToken);
        mockMvc.perform(get("/kds/board").header("Authorization", "Bearer " + legonCashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.CONFIRMED[?(@.orderId == '%s')]".formatted(orderId)).exists());
    }

    private void payCash(UUID orderId, String token) throws Exception {
        mockMvc.perform(post("/payments/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"method\":\"CASH\",\"idempotencyKey\":\"cash-%s\",\"amountTendered\":500}".formatted(orderId, orderId)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ helpers

    private UUID placeOrder(String body, UUID expectedBranch) throws Exception {
        String response = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var json = objectMapper.readTree(response);
        if (expectedBranch != null) {
            assertEquals(expectedBranch.toString(), json.get("branchId").asText());
        }
        return UUID.fromString(json.get("id").asText());
    }

    private void updateStatus(UUID orderId, String token, int expected) throws Exception {
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().is(expected));
    }

    private UUID zone(String name, UUID branchId) throws Exception {
        String body = mockMvc.perform(post("/delivery/zones")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"branchId\":\"%s\",\"fee\":10,\"minOrderAmount\":0,\"estimatedMinutes\":30}".formatted(name, branchId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String cashierToken(String phone, BranchEntity branch) {
        UserEntity cashier = createUser("Cashier " + branch.getName(), phone, null, "secret123", Role.CASHIER);
        cashier.setBranch(branch);
        userRepository.save(cashier);
        return jwtService.generateAccessToken(cashier.getId(), Role.CASHIER, branch.getId());
    }

    private BranchEntity branch(String code, String name) {
        BranchEntity branch = new BranchEntity();
        branch.setCode(code);
        branch.setName(name);
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private MenuItemEntity menuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Branch-" + name);
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity saved = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(saved);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }

    private String item() {
        return "{\"menuItemId\":\"%s\",\"quantity\":1}".formatted(dish.getId());
    }
}
