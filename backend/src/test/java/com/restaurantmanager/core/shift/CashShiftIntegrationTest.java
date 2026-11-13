package com.restaurantmanager.core.shift;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.payment.PaymentService;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The drawer count: cash by who took it, cash refunds, pay-ins/outs, the open-shift rule and history. */
@TestPropertySource(properties = "app.shifts.require-open-shift-for-cash=true")
class CashShiftIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    private UserEntity ama;      // cashier
    private UserEntity kofi;     // cashier
    private UserEntity manager;
    private UserEntity customer;
    private MenuItemEntity dish; // 50.00

    @BeforeEach
    void setUpData() {
        ama = createUser("Ama", "+233291000001", "ama.shift@x.com", "secret123", Role.CASHIER);
        kofi = createUser("Kofi", "+233291000002", "kofi.shift@x.com", "secret123", Role.CASHIER);
        manager = createUser("Mgr", "+233291000003", "mgr.shift@x.com", "secret123", Role.MANAGER);
        customer = createUser("Cust", "+233291000004", "cust.shift@x.com", "secret123", Role.CUSTOMER);
        dish = createMenuItem("Shift dish", "50.00");
    }

    @Test
    void takingCashWithoutAnOpenShift_isRefused() throws Exception {
        String order = pickupOrder(ama);
        payCash(ama, order).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Open a cash shift before handling cash (Shifts page)"));
    }

    @Test
    void drawerCountsCashByWhoTookIt_minusRefunds_plusMovements() throws Exception {
        String shift = openShift(ama, 100);

        // A customer's app order paid in cash at the counter: Ama did not create it, but she took the money.
        String customerOrder = pickupOrder(customer);
        JsonNode payment = json(payCash(ama, customerOrder).andExpect(status().isCreated()));
        // Ama rings up an order; Kofi takes the cash for it. It is Kofi's cash, not Ama's.
        openShift(kofi, 0);
        String amasOrder = pickupOrder(ama);
        payCash(kofi, amasOrder).andExpect(status().isCreated());

        // Ama hands back 10 in cash.
        paymentService.refund(UUID.fromString(payment.get("paymentId").asText()), new BigDecimal("10.00"), "Missing drink", ama.getId(), manager.getId());

        movement(ama, shift, "PAY_IN", "20", "Float top-up").andExpect(status().isOk());
        movement(ama, shift, "PAY_OUT", "5", "Ice from the shop").andExpect(status().isOk());

        // 100 float + 50 sale - 10 refund + 20 in - 5 out = 155
        mockMvc.perform(get("/shifts/" + shift).header("Authorization", bearer(ama)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashSalesCount").value(1))
                .andExpect(jsonPath("$.cashSales").value(50.0))
                .andExpect(jsonPath("$.cashRefunds").value(10.0))
                .andExpect(jsonPath("$.payIns").value(20.0))
                .andExpect(jsonPath("$.payOuts").value(5.0))
                .andExpect(jsonPath("$.expectedCash").value(155.0))
                .andExpect(jsonPath("$.movements.length()").value(2))
                .andExpect(jsonPath("$.movements[1].createdByName").value("Ama"));

        mockMvc.perform(post("/shifts/" + shift + "/close")
                        .header("Authorization", bearer(ama))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingCash\":150,\"notes\":\"5 short\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(155.0))
                .andExpect(jsonPath("$.variance").value(-5.0));
    }

    @Test
    void payOutCannotExceedTheDrawer_andClosedShiftsTakeNoMovements() throws Exception {
        String shift = openShift(ama, 30);
        movement(ama, shift, "PAY_OUT", "31", "Too much").andExpect(status().isConflict());
        mockMvc.perform(post("/shifts/" + shift + "/close")
                        .header("Authorization", bearer(ama))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingCash\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variance").value(0.0));
        movement(ama, shift, "PAY_IN", "5", "Late").andExpect(status().isConflict());
    }

    @Test
    void anotherCashierCannotMoveCashInMyDrawer_butAManagerCan() throws Exception {
        String shift = openShift(ama, 50);
        movement(kofi, shift, "PAY_OUT", "5", "Not mine").andExpect(status().isForbidden());
        movement(manager, shift, "PAY_OUT", "5", "Bank drop").andExpect(status().isOk());
    }

    @Test
    void history_cashierSeesOwn_managerSeesAll_withFigures() throws Exception {
        String amaShift = openShift(ama, 10);
        mockMvc.perform(post("/shifts/" + amaShift + "/close")
                        .header("Authorization", bearer(ama))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingCash\":8}"))
                .andExpect(status().isOk());
        openShift(kofi, 0);

        mockMvc.perform(get("/shifts").header("Authorization", bearer(ama)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].variance").value(-2.0));
        mockMvc.perform(get("/shifts").header("Authorization", bearer(manager)))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/shifts/" + amaShift).header("Authorization", bearer(kofi)))
                .andExpect(status().isForbidden());
    }

    private String openShift(UserEntity who, int opening) throws Exception {
        return json(mockMvc.perform(post("/shifts/open")
                        .header("Authorization", bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\":" + opening + "}"))
                .andExpect(status().isOk())).get("id").asText();
    }

    private ResultActions movement(UserEntity who, String shift, String type, String amount, String reason) throws Exception {
        return mockMvc.perform(post("/shifts/" + shift + "/movements")
                .header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"amount\":" + amount + ",\"reason\":\"" + reason + "\"}"));
    }

    private String pickupOrder(UserEntity who) throws Exception {
        return json(mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + dish.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions payCash(UserEntity who, String orderId) throws Exception {
        return mockMvc.perform(post("/payments/initiate")
                .header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\",\"method\":\"CASH\",\"idempotencyKey\":\"cash-" + UUID.randomUUID() + "\",\"amountTendered\":100}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer(UserEntity user) {
        return "Bearer " + accessToken(user);
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Shift-" + name + "-" + System.nanoTime());
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
