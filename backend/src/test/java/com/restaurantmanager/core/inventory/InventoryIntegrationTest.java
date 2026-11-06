package com.restaurantmanager.core.inventory;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private IngredientRepository ingredientRepository;

    private UserEntity manager;
    private UserEntity customer;
    private MenuItemEntity fufu;
    private UUID cassava;

    @BeforeEach
    void setUpData() throws Exception {
        manager = createUser("Manager", "+233310000001", "inv-manager@x.com", "secret123", Role.MANAGER);
        customer = createUser("Adwoa", "+233310000002", "adwoa@x.com", "secret123", Role.CUSTOMER);
        fufu = createMenuItem("Fufu", "40.00");
        cassava = createIngredient("Cassava", "kg", "1.0", "3.0");
        mockMvc.perform(put("/inventory/menu-items/" + fufu.getId() + "/recipe")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ingredientId\":\"" + cassava + "\",\"quantity\":0.5}]"))
                .andExpect(status().isOk());
    }

    @Test
    void givenRecipe_whenOrdered_thenStockDeductedAndLowStockFlagged() throws Exception {
        order(2); // uses 1.0 kg of 3.0
        assertEquals(0, new BigDecimal("2.000").compareTo(stock()));

        order(2); // 1.0 kg left -> at threshold
        mockMvc.perform(get("/inventory/low-stock")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cassava"));
        assertTrue(ingredientRepository.findById(cassava).orElseThrow().isLowStockAlerted());
    }

    @Test
    void givenInsufficientStock_whenOrdered_then409AndNothingDeducted() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(7)))
                .andExpect(status().isConflict());
        assertEquals(0, new BigDecimal("3.000").compareTo(stock()));
    }

    @Test
    void givenLastPortionsSold_whenStockRunsOut_thenItemAuto86dAndRestockBringsItBack() throws Exception {
        order(6); // uses all 3.0 kg
        MenuItemEntity after = menuItemRepository.findById(fufu.getId()).orElseThrow();
        assertFalse(after.isAvailable());
        assertTrue(after.isAuto86());

        mockMvc.perform(post("/inventory/ingredients/" + cassava + "/restock")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10,\"note\":\"Market run\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(10.0))
                .andExpect(jsonPath("$.low").value(false));
        MenuItemEntity back = menuItemRepository.findById(fufu.getId()).orElseThrow();
        assertTrue(back.isAvailable());
        assertFalse(back.isAuto86());
    }

    @Test
    void givenUnpreparedOrder_whenCancelled_thenStockRestored() throws Exception {
        UUID orderId = order(2);
        assertEquals(0, new BigDecimal("2.000").compareTo(stock()));

        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().is2xxSuccessful());

        assertEquals(0, new BigDecimal("3.000").compareTo(stock()));
        mockMvc.perform(get("/inventory/ingredients/" + cassava + "/movements")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(jsonPath("$[0].reason").value("ORDER_CANCELLED"));
    }

    @Test
    void givenWaste_whenRecorded_thenStockReducedAndCountCorrectionSetsLevel() throws Exception {
        mockMvc.perform(post("/inventory/ingredients/" + cassava + "/adjust")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"WASTE\",\"delta\":0.5,\"note\":\"Spoiled\"}"))
                .andExpect(jsonPath("$.stockQuantity").value(2.5));
        mockMvc.perform(post("/inventory/ingredients/" + cassava + "/adjust")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ADJUSTMENT\",\"countedQuantity\":4}"))
                .andExpect(jsonPath("$.stockQuantity").value(4.0));
    }

    @Test
    void givenCustomer_whenManagingInventory_then403() throws Exception {
        mockMvc.perform(get("/inventory/ingredients")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isForbidden());
    }

    private UUID createIngredient(String name, String unit, String threshold, String initial) throws Exception {
        String body = mockMvc.perform(post("/inventory/ingredients")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"unit\":\"%s\",\"lowStockThreshold\":%s,\"initialStock\":%s}".formatted(name, unit, threshold, initial)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID order(int quantity) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String orderBody(int quantity) {
        return "{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + fufu.getId() + "\",\"quantity\":" + quantity + "}]}";
    }

    private BigDecimal stock() {
        return ingredientRepository.findById(cassava).orElseThrow().getStockQuantity();
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Inv-" + name + "-" + Instant.now().toEpochMilli());
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
