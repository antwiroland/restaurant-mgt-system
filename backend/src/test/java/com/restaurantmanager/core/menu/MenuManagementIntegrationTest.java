package com.restaurantmanager.core.menu;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuManagementIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenAdminToken_whenCreateCategory_then201() throws Exception {
        UserEntity admin = createUser("Admin", "+233200100001", "admin+menu1@x.com", "secret123", Role.ADMIN);

        mockMvc.perform(post("/menu/categories")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Starters\"," +
                                "\"description\":\"Light bites\"," +
                                "\"displayOrder\":1," +
                                "\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Starters"));
    }

    @Test
    void givenCashierToken_whenCreateCategory_then403() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233200100002", "cashier+menu1@x.com", "secret123", Role.CASHIER);

        mockMvc.perform(post("/menu/categories")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Starters\"," +
                                "\"description\":\"Light bites\"," +
                                "\"displayOrder\":1," +
                                "\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenNoAuth_whenListCategories_then200PublicAccess() throws Exception {
        createCategory("Main", 2, true);

        mockMvc.perform(get("/menu/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void givenMultipleCategories_whenList_thenReturnedSortedByDisplayOrder() throws Exception {
        createCategory("Desserts", 3, true);
        createCategory("Starters", 1, true);
        createCategory("Mains", 2, true);

        mockMvc.perform(get("/menu/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Starters"))
                .andExpect(jsonPath("$[1].name").value("Mains"))
                .andExpect(jsonPath("$[2].name").value("Desserts"));
    }

    @Test
    void givenAdminToken_whenDeleteCategory_then204() throws Exception {
        UserEntity admin = createUser("Admin", "+233200100003", "admin+menu2@x.com", "secret123", Role.ADMIN);
        CategoryEntity category = createCategory("ToDelete", 0, true);

        mockMvc.perform(delete("/menu/categories/" + category.getId())
                        .header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void givenManagerToken_whenDeleteCategory_then403() throws Exception {
        UserEntity manager = createUser("Manager", "+233200100004", "manager+menu1@x.com", "secret123", Role.MANAGER);
        CategoryEntity category = createCategory("NoDelete", 0, true);

        mockMvc.perform(delete("/menu/categories/" + category.getId())
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenAdminToken_whenCreateMenuItem_then201WithCorrectFields() throws Exception {
        UserEntity admin = createUser("Admin", "+233200100005", "admin+menu3@x.com", "secret123", Role.ADMIN);
        CategoryEntity category = createCategory("Mains", 1, true);

        mockMvc.perform(post("/menu/items")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"categoryId\":\"" + category.getId() + "\"," +
                                "\"name\":\"Jollof Rice\"," +
                                "\"description\":\"Smoky rice\"," +
                                "\"price\":25.00," +
                                "\"imageUrl\":\"https://img/jollof.png\"," +
                                "\"available\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jollof Rice"))
                .andExpect(jsonPath("$.price").value(25.00));
    }

    @Test
    void givenNoAuth_whenListMenuItems_then200() throws Exception {
        CategoryEntity category = createCategory("Mains", 1, true);
        createItem(category, "Waakye", true, new BigDecimal("20.00"));

        mockMvc.perform(get("/menu/items"))
                .andExpect(status().isOk());
    }

    @Test
    void givenAvailableFalse_whenListPublicMenu_thenItemExcluded() throws Exception {
        CategoryEntity category = createCategory("Mains", 1, true);
        createItem(category, "Banku", false, new BigDecimal("18.00"));

        mockMvc.perform(get("/menu/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void givenSearchQuery_whenListMenuItems_thenOnlyMatchingItemsReturned() throws Exception {
        CategoryEntity category = createCategory("Mains", 1, true);
        createItem(category, "Chicken Jollof", true, new BigDecimal("30.00"));
        createItem(category, "Beef Stew", true, new BigDecimal("28.00"));

        mockMvc.perform(get("/menu/items").param("q", "chicken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chicken Jollof"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void givenManagerToken_whenToggleAvailabilityToFalse_thenItemUnavailableInPublicList() throws Exception {
        UserEntity manager = createUser("Manager", "+233200100006", "manager+menu2@x.com", "secret123", Role.MANAGER);
        CategoryEntity category = createCategory("Mains", 1, true);
        MenuItemEntity item = createItem(category, "Kenkey", true, new BigDecimal("15.00"));

        mockMvc.perform(patch("/menu/items/" + item.getId() + "/availability")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/menu/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void givenCashierToken_whenToggleAvailability_then200() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233200100007", "cashier+menu2@x.com", "secret123", Role.CASHIER);
        CategoryEntity category = createCategory("Mains", 1, true);
        MenuItemEntity item = createItem(category, "Tilapia", true, new BigDecimal("40.00"));

        mockMvc.perform(patch("/menu/items/" + item.getId() + "/availability")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void givenCustomerToken_whenToggleAvailability_then403() throws Exception {
        UserEntity customer = createUser("Customer", "+233200100008", "customer+menu1@x.com", "secret123", Role.CUSTOMER);
        CategoryEntity category = createCategory("Mains", 1, true);
        MenuItemEntity item = createItem(category, "Fufu", true, new BigDecimal("32.00"));

        mockMvc.perform(patch("/menu/items/" + item.getId() + "/availability")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isForbidden());
    }

    private CategoryEntity createCategory(String name, int displayOrder, boolean active) {
        CategoryEntity category = new CategoryEntity();
        category.setName(name);
        category.setDescription(name + " description");
        category.setDisplayOrder(displayOrder);
        category.setActive(active);
        return categoryRepository.save(category);
    }

    private MenuItemEntity createItem(CategoryEntity category, String name, boolean available, BigDecimal price) {
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(category);
        item.setName(name);
        item.setDescription(name + " description");
        item.setPrice(price);
        item.setImageUrl("https://img/" + name + ".png");
        item.setAvailable(available);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}
