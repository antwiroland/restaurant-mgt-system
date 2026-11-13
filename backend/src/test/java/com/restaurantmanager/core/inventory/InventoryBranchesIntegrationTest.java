package com.restaurantmanager.core.inventory;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuModifierGroupEntity;
import com.restaurantmanager.core.menu.MenuModifierOptionEntity;
import com.restaurantmanager.core.menu.ModifierSelectionType;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stock per branch, modifier recipes, inactive ingredients and food cost. */
class InventoryBranchesIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BranchRepository branchRepository;

    private UserEntity admin;
    private UserEntity customer;
    private BranchEntity osu;
    private BranchEntity legon;
    private MenuItemEntity rice;   // 40.00, recipe 0.5 kg grains
    private UUID grains;

    @BeforeEach
    void setUpData() throws Exception {
        admin = createUser("Admin", "+233320000001", "inv-admin@x.com", "secret123", Role.ADMIN);
        customer = createUser("Cust", "+233320000002", "inv-cust@x.com", "secret123", Role.CUSTOMER);
        osu = branch("OSU");
        legon = branch("LEG");
        rice = createMenuItem("Rice bowl", "40.00");
        grains = json(mockMvc.perform(post("/inventory/ingredients")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Grains\",\"unit\":\"kg\",\"lowStockThreshold\":0.5,\"costPerUnit\":10}"))
                .andExpect(status().isCreated())).get("id").asText().transform(UUID::fromString);
        mockMvc.perform(put("/inventory/menu-items/" + rice.getId() + "/recipe")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ingredientId\":\"" + grains + "\",\"quantity\":0.5}]"))
                .andExpect(status().isOk());
    }

    @Test
    void stockIsPerBranch_andAnUntrackedBranchIsNotBlocked() throws Exception {
        restock(osu, "1.0", null, null).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(1.0));

        order(osu, 2).andExpect(status().isCreated());            // uses Osu's 1.0 kg
        order(osu, 1).andExpect(status().isConflict());           // Osu is out
        order(legon, 3).andExpect(status().isCreated());          // Legon does not track grains: not blocked

        mockMvc.perform(get("/inventory/ingredients").param("branchId", osu.getId().toString()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[0].stockQuantity").value(0.0))
                .andExpect(jsonPath("$[0].tracked").value(true));
        mockMvc.perform(get("/inventory/ingredients").param("branchId", legon.getId().toString()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[0].tracked").value(false));
    }

    @Test
    void soldOutAtOneBranch_staysOnTheMenu_withThatBranchNamed() throws Exception {
        restock(osu, "0.5", null, null).andExpect(status().isOk());
        restock(legon, "5", null, null).andExpect(status().isOk());
        order(osu, 1).andExpect(status().isCreated());            // Osu now has 0

        mockMvc.perform(get("/menu/items/" + rice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.soldOutBranchIds", contains(osu.getId().toString())));

        restock(osu, "2", null, null).andExpect(status().isOk());
        mockMvc.perform(get("/menu/items/" + rice.getId()))
                .andExpect(jsonPath("$.soldOutBranchIds").isEmpty());
    }

    @Test
    void changingStockWithoutABranch_isRefusedOnceThereAreBranches() throws Exception {
        mockMvc.perform(post("/inventory/ingredients/" + grains + "/restock")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose the branch whose stock this is"));
    }

    @Test
    void modifierOption_usesItsOwnIngredientsPerUnit() throws Exception {
        MenuModifierOptionEntity large = option(rice, "Size", "Large");
        mockMvc.perform(put("/inventory/modifier-options/" + large.getId() + "/recipe")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ingredientId\":\"" + grains + "\",\"quantity\":0.25}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(0.25));
        restock(osu, "10", null, null).andExpect(status().isOk());

        // 2 x (0.5 recipe + 0.25 Large) = 1.5 kg
        mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"branchId\":\"" + osu.getId() + "\",\"items\":[{\"menuItemId\":\"" + rice.getId()
                                + "\",\"quantity\":2,\"modifierOptionIds\":[\"" + large.getId() + "\"]}]}"))
                .andExpect(status().isCreated());
        assertThat(stockAt(osu)).isEqualByComparingTo("8.5");
    }

    @Test
    void inactiveIngredient_isNotUsedAndDoesNotBlock() throws Exception {
        restock(osu, "0.5", null, null).andExpect(status().isOk());
        order(osu, 1).andExpect(status().isCreated());            // Osu at 0, rice sold out there
        assertThat(menuItemStockoutRepository.findByMenuItemId(rice.getId())).hasSize(1);

        mockMvc.perform(put("/inventory/ingredients/" + grains)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Grains\",\"unit\":\"kg\",\"lowStockThreshold\":0.5,\"costPerUnit\":10,\"active\":false}"))
                .andExpect(status().isOk());

        assertThat(menuItemStockoutRepository.findByMenuItemId(rice.getId())).isEmpty();
        order(osu, 4).andExpect(status().isCreated());            // not blocked
        assertThat(stockAt(osu)).isEqualByComparingTo("0");      // and nothing used
    }

    @Test
    void foodCost_usesThePurchasePriceAtTheTime_againstNetSales() throws Exception {
        restock(osu, "10", "12", "Makola Market")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPerUnit").value(12.0));   // latest purchase price becomes the cost
        order(osu, 2).andExpect(status().isCreated());              // 1.0 kg at 12 = 12.00, sales 80.00

        mockMvc.perform(get("/inventory/ingredients/" + grains + "/movements").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[1].supplier").value("Makola Market"))
                .andExpect(jsonPath("$[1].unitCost").value(12.0));

        mockMvc.perform(get("/inventory/reports/food-cost").param("branchId", osu.getId().toString()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netSales").value(80.0))
                .andExpect(jsonPath("$.usedCost").value(12.0))
                .andExpect(jsonPath("$.purchasesCost").value(120.0))
                .andExpect(jsonPath("$.foodCostPercent").value(15.0))
                .andExpect(jsonPath("$.ingredients[0].usedQuantity").value(1.0))
                // Rice bowl: 0.5 kg x 12 = 6.00 of a 40.00 price -> 85% margin
                .andExpect(jsonPath("$.dishes[0].recipeCost").value(6.0))
                .andExpect(jsonPath("$.dishes[0].marginPercent").value(85.0));
    }

    private ResultActions restock(BranchEntity branch, String quantity, String unitCost, String supplier) throws Exception {
        String body = "{\"quantity\":" + quantity + ",\"branchId\":\"" + branch.getId() + "\""
                + (unitCost == null ? "" : ",\"unitCost\":" + unitCost)
                + (supplier == null ? "" : ",\"supplier\":\"" + supplier + "\"") + "}";
        return mockMvc.perform(post("/inventory/ingredients/" + grains + "/restock")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions order(BranchEntity branch, int quantity) throws Exception {
        return mockMvc.perform(post("/orders")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PICKUP\",\"branchId\":\"" + branch.getId() + "\",\"items\":[{\"menuItemId\":\"" + rice.getId()
                        + "\",\"quantity\":" + quantity + "}]}"));
    }

    private BigDecimal stockAt(BranchEntity branch) {
        return ingredientStockRepository.findByIngredient_Id(grains).stream()
                .filter(row -> branch.getId().equals(row.getBranchId()))
                .findFirst().orElseThrow().getStockQuantity();
    }

    private BranchEntity branch(String code) {
        BranchEntity branch = new BranchEntity();
        branch.setCode(code + java.util.UUID.randomUUID().toString().substring(0, 8));
        branch.setName("Branch " + code);
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private MenuModifierOptionEntity option(MenuItemEntity dish, String groupName, String optionName) {
        MenuModifierGroupEntity group = new MenuModifierGroupEntity();
        group.setMenuItem(dish);
        group.setName(groupName);
        group.setSelectionType(ModifierSelectionType.SINGLE);
        group.setRequired(false);
        group.setDisplayOrder(1);
        group.setActive(true);
        MenuModifierGroupEntity saved = menuModifierGroupRepository.save(group);
        MenuModifierOptionEntity option = new MenuModifierOptionEntity();
        option.setGroup(saved);
        option.setName(optionName);
        option.setPriceDelta(BigDecimal.ZERO);
        option.setDisplayOrder(1);
        option.setActive(true);
        return menuModifierOptionRepository.save(option);
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("InvB-" + name + "-" + System.nanoTime());
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

    private com.fasterxml.jackson.databind.JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer(UserEntity user) {
        return "Bearer " + accessToken(user);
    }
}
