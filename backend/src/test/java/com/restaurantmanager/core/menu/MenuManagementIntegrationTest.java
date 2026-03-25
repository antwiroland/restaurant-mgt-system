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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void givenManagerToken_whenCreateAndListModifiers_then201AndVisible() throws Exception {
        UserEntity manager = createUser("Manager", "+233200100009", "manager+menu3@x.com", "secret123", Role.MANAGER);
        CategoryEntity category = createCategory("Mains", 1, true);
        MenuItemEntity item = createItem(category, "Grilled Chicken", true, new BigDecimal("45.00"));

        String groupResponse = mockMvc.perform(post("/menu/items/" + item.getId() + "/modifiers")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Size\"," +
                                "\"selectionType\":\"SINGLE\"," +
                                "\"required\":true," +
                                "\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Size"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String groupId = objectMapper.readTree(groupResponse).path("id").asText();

        mockMvc.perform(post("/menu/items/" + item.getId() + "/modifiers/" + groupId + "/options")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Large\"," +
                                "\"priceDelta\":5.00," +
                                "\"displayOrder\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Large"))
                .andExpect(jsonPath("$.priceDelta").value(5.00));

        mockMvc.perform(post("/menu/items/" + item.getId() + "/modifiers/" + groupId + "/options")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Regular\"," +
                                "\"priceDelta\":0.00," +
                                "\"displayOrder\":1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/menu/items/" + item.getId() + "/modifiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Size"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].options[0].name").value("Regular"))
                .andExpect(jsonPath("$[0].options[1].name").value("Large"));
    }

    @Test
    void givenCashierToken_whenCreateModifierGroup_then403() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233200100010", "cashier+menu3@x.com", "secret123", Role.CASHIER);
        CategoryEntity category = createCategory("Mains", 1, true);
        MenuItemEntity item = createItem(category, "Pepper Soup", true, new BigDecimal("22.00"));

        mockMvc.perform(post("/menu/items/" + item.getId() + "/modifiers")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Spice\"," +
                                "\"selectionType\":\"SINGLE\"," +
                                "\"required\":false," +
                                "\"displayOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenManagerToken_whenDeleteModifierOption_thenNoLongerVisible() throws Exception {
        UserEntity manager = createUser("Manager", "+233200100011", "manager+menu4@x.com", "secret123", Role.MANAGER);
        CategoryEntity category = createCategory("Mains", 1, true);
        MenuItemEntity item = createItem(category, "Rice Bowl", true, new BigDecimal("18.00"));

        String groupResponse = mockMvc.perform(post("/menu/items/" + item.getId() + "/modifiers")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Spice Level\"," +
                                "\"selectionType\":\"SINGLE\"," +
                                "\"required\":false," +
                                "\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String groupId = objectMapper.readTree(groupResponse).path("id").asText();

        String optionResponse = mockMvc.perform(post("/menu/items/" + item.getId() + "/modifiers/" + groupId + "/options")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Hot\"," +
                                "\"priceDelta\":0.00," +
                                "\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String optionId = objectMapper.readTree(optionResponse).path("id").asText();

        mockMvc.perform(delete("/menu/items/" + item.getId() + "/modifiers/" + groupId + "/options/" + optionId)
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/menu/items/" + item.getId() + "/modifiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].options").isEmpty());

        mockMvc.perform(put("/menu/items/" + item.getId() + "/modifiers/" + groupId)
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"name\":\"Spice\"," +
                                "\"selectionType\":\"SINGLE\"," +
                                "\"required\":false," +
                                "\"displayOrder\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spice"));
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
