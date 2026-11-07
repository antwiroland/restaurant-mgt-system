package com.restaurantmanager.core.billing;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private TaxRateRepository taxRateRepository;

    private UserEntity admin;
    private UserEntity cashier;
    private MenuItemEntity meal;

    @BeforeEach
    void setUpData() {
        admin = createUser("Admin", "+233290000001", "bill-admin@x.com", "secret123", Role.ADMIN);
        cashier = createUser("Cashier", "+233290000002", "bill-cashier@x.com", "secret123", Role.CASHIER);
        meal = createMenuItem("Banku & Tilapia", "100.00");
        addTax("NHIL", "2.5", false, 1);
        addTax("GETFUND", "2.5", false, 2);
        addTax("VAT", "15", true, 3);
    }

    @Test
    void givenGhanaTaxes_whenPickupOrderCreated_thenLeviesAndVatAddedToTotal() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("PICKUP", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(100.0))
                .andExpect(jsonPath("$.taxTotal").value(20.75))
                .andExpect(jsonPath("$.taxLines.length()").value(3))
                .andExpect(jsonPath("$.taxLines[2].code").value("VAT"))
                .andExpect(jsonPath("$.taxLines[2].amount").value(15.75))
                .andExpect(jsonPath("$.serviceCharge").value(0.0))
                .andExpect(jsonPath("$.total").value(120.75));
    }

    @Test
    void givenServiceChargeForDineIn_whenDineInAndPickupOrdered_thenOnlyDineInCharged() throws Exception {
        updateSettings("10", true, false, true);
        RestaurantTableEntity table = createTable("B1");

        // dine-in: 100 + 10 service = 110 taxable -> 22.83 tax -> 132.83
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("DINE_IN", table.getId(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceCharge").value(10.0))
                .andExpect(jsonPath("$.taxTotal").value(22.83))
                .andExpect(jsonPath("$.total").value(132.83));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("PICKUP", null, null)))
                .andExpect(jsonPath("$.serviceCharge").value(0.0));
    }

    @Test
    void givenTaxInclusivePrices_whenOrdered_thenTotalEqualsMenuPriceWithTaxShown() throws Exception {
        updateSettings("0", true, true, true);
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("PICKUP", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxInclusive").value(true))
                .andExpect(jsonPath("$.taxTotal").value(17.18))
                .andExpect(jsonPath("$.total").value(100.0));
    }

    @Test
    void givenTip_whenAddedAtCreationAndChangedLater_thenTotalIncludesTipUntaxed() throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("PICKUP", null, "5.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipAmount").value(5.0))
                .andExpect(jsonPath("$.total").value(125.75))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(put("/orders/" + orderId + "/tip")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percent\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipAmount").value(10.0))
                .andExpect(jsonPath("$.total").value(130.75));
    }

    @Test
    void givenCart_whenQuoted_thenSameBreakdownWithoutCreatingOrder() throws Exception {
        mockMvc.perform(post("/orders/public/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("PICKUP", null, "2.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxTotal").value(20.75))
                .andExpect(jsonPath("$.total").value(122.75));
        org.junit.jupiter.api.Assertions.assertEquals(0, orderRepository.count());
    }

    @Test
    void givenAdmin_whenSettingsAndTaxesManaged_thenPublicConfigReflectsThem() throws Exception {
        mockMvc.perform(post("/billing/taxes")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TOURISM\",\"name\":\"Tourism Levy\",\"rate\":1,\"compound\":false,\"displayOrder\":4}"))
                .andExpect(status().isCreated());
        updateSettings("5", false, false, true);

        mockMvc.perform(get("/billing/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceChargePercent").value(5.0))
                .andExpect(jsonPath("$.taxes.length()").value(4));

        mockMvc.perform(put("/billing/settings")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceChargePercent\":1,\"serviceChargeDineInOnly\":true,\"pricesIncludeTax\":false,\"taxDeliveryFee\":true,\"tipsEnabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenManualDiscount_whenApplied_thenTaxesRecomputedOnDiscountedAmount() throws Exception {
        UserEntity manager = createUser("Mgr", "+233290000003", "bill-mgr@x.com", "secret123", Role.MANAGER);
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("PICKUP", null, null)))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        String token = jwtService.generateOverrideToken(manager.getId(), Role.MANAGER, com.restaurantmanager.core.common.OverrideActionType.DISCOUNT);

        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrideToken\":\"" + token + "\",\"orderId\":\"" + orderId + "\",\"mode\":\"FLAT\",\"value\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newTotal").value(96.6)); // 80 + 20.75% = 96.60
    }

    private void updateSettings(String servicePercent, boolean dineInOnly, boolean inclusive, boolean tips) throws Exception {
        mockMvc.perform(put("/billing/settings")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceChargePercent\":%s,\"serviceChargeDineInOnly\":%s,\"pricesIncludeTax\":%s,\"taxDeliveryFee\":true,\"tipsEnabled\":%s}"
                                .formatted(servicePercent, dineInOnly, inclusive, tips)))
                .andExpect(status().isOk());
    }

    private void addTax(String code, String rate, boolean compound, int order) {
        TaxRateEntity tax = new TaxRateEntity();
        tax.setCode(code);
        tax.setName(code);
        tax.setRate(new BigDecimal(rate));
        tax.setCompound(compound);
        tax.setDisplayOrder(order);
        tax.setActive(true);
        taxRateRepository.save(tax);
    }

    private String order(String type, UUID tableId, String tip) {
        return "{\"type\":\"" + type + "\"" + (tableId == null ? "" : ",\"tableId\":\"" + tableId + "\"")
                + ",\"items\":[{\"menuItemId\":\"" + meal.getId() + "\",\"quantity\":1}]"
                + (tip == null ? "" : ",\"tipAmount\":" + tip) + "}";
    }

    private RestaurantTableEntity createTable(String number) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(number + "-" + Instant.now().toEpochMilli());
        return restaurantTableRepository.save(table);
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Billing-" + name + "-" + Instant.now().toEpochMilli());
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
