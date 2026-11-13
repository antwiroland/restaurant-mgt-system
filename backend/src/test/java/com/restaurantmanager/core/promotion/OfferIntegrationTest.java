package com.restaurantmanager.core.promotion;

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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Offer kinds through the API: management, the public list, quotes with suggestions, branch and happy hour. */
class OfferIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BranchRepository branchRepository;

    private UserEntity manager;
    private UserEntity customer;
    private MenuItemEntity burger;
    private MenuItemEntity soda;
    private MenuItemEntity fries;

    @BeforeEach
    void setUpData() {
        manager = createUser("Mgr", "+233290000001", "mgr.offers@x.com", "secret123", Role.MANAGER);
        customer = createUser("Cust", "+233290000002", "cust.offers@x.com", "secret123", Role.CUSTOMER);
        burger = createMenuItem("Burger", "80.00");
        soda = createMenuItem("Soda", "15.00");
        fries = createMenuItem("Fries", "20.00");
    }

    @Test
    void buyBurgerGetSoda_quoteShowsHintThenDiscount() throws Exception {
        createOffer("""
                {"name":"Burger + free soda","offerType":"BUY_X_GET_OTHER","menuItemId":"%s","buyQuantity":1,
                 "freeMenuItemId":"%s","freeQuantity":1}""".formatted(burger.getId(), soda.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary").value("Buy 1 Burger, get 1 Soda free"));

        quote(cart(burger, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerDiscount").value(0))
                .andExpect(jsonPath("$.offerHints[0].menuItemId").value(soda.getId().toString()))
                .andExpect(jsonPath("$.offerHints[0].message").value("Add 1 Soda: free with your Burger"));

        quote(cart(burger, 1) + "," + item(soda, 1))
                .andExpect(jsonPath("$.offerDiscount").value(15.0))
                .andExpect(jsonPath("$.appliedOffers[0].name").value("Burger + free soda"))
                .andExpect(jsonPath("$.offerHints").isEmpty());
    }

    @Test
    void percentOffAndCombo_appliedAtOrderCreation() throws Exception {
        createOffer("""
                {"name":"Fries 50%%","offerType":"PERCENT_OFF","menuItemId":"%s","percentOff":50}""".formatted(fries.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary").value("50% off Fries"));
        createOffer("""
                {"name":"Lunch combo","offerType":"COMBO","comboPrice":85,
                 "comboItems":[{"menuItemId":"%s","quantity":1},{"menuItemId":"%s","quantity":1}]}""".formatted(burger.getId(), soda.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary").value("Burger + Soda for 85.00"));

        // Fries 20 -> 10 off; burger 80 + soda 15 = 95 -> combo 85 saves 10.
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[" + item(burger, 1) + "," + item(soda, 1) + "," + item(fries, 1) + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offerDiscount").value(20.0));
    }

    @Test
    void invalidOffers_rejected() throws Exception {
        createOffer("""
                {"name":"No pct","offerType":"PERCENT_OFF","menuItemId":"%s"}""".formatted(fries.getId()))
                .andExpect(status().isBadRequest());
        createOffer("""
                {"name":"One-dish combo","offerType":"COMBO","comboPrice":10,"comboItems":[{"menuItemId":"%s","quantity":1}]}""".formatted(fries.getId()))
                .andExpect(status().isBadRequest());
        createOffer("""
                {"name":"Half window","menuItemId":"%s","buyQuantity":1,"freeQuantity":1,"dailyStartTime":"16:00"}""".formatted(fries.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicList_isOpen_andShowsScheduleAndWhetherRunningNow() throws Exception {
        // A happy hour that is certainly not now: a one-minute window twelve hours away.
        LocalTime later = ZonedDateTime.now(ZoneId.of("Africa/Accra")).toLocalTime().plusHours(12).withSecond(0).withNano(0);
        createOffer("""
                {"name":"Happy hour fries","offerType":"PERCENT_OFF","menuItemId":"%s","percentOff":20,
                 "daysOfWeek":["MON","FRI"],"dailyStartTime":"%s","dailyEndTime":"%s"}"""
                .formatted(fries.getId(), later, later.plusMinutes(1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/promotions/offers/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary").value("20% off Fries"))
                .andExpect(jsonPath("$[0].runningNow").value(false))
                .andExpect(jsonPath("$[0].schedule").value(org.hamcrest.Matchers.startsWith("Mon, Fri · ")))
                .andExpect(jsonPath("$[0].menuItemIds", hasItem(fries.getId().toString())));

        // Outside its hours it is advertised but not applied.
        quote(cart(fries, 1)).andExpect(jsonPath("$.offerDiscount").value(0));
    }

    @Test
    void branchOffer_appliesOnlyToOrdersAtThatBranch() throws Exception {
        BranchEntity osu = branch("OSU");
        BranchEntity legon = branch("LEG");
        createOffer("""
                {"name":"Osu soda","offerType":"PERCENT_OFF","menuItemId":"%s","percentOff":100,"branchId":"%s"}"""
                .formatted(soda.getId(), osu.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchName").value("Branch OSU"));

        quote(cart(soda, 1), osu).andExpect(jsonPath("$.offerDiscount").value(15.0));
        quote(cart(soda, 1), legon).andExpect(jsonPath("$.offerDiscount").value(0));

        mockMvc.perform(get("/promotions/offers/public").param("branchId", legon.getId().toString()))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/promotions/offers/public").param("branchId", osu.getId().toString()))
                .andExpect(jsonPath("$[0].branchName").value("Branch OSU"));
    }

    private ResultActions createOffer(String body) throws Exception {
        return mockMvc.perform(post("/promotions/offers")
                .header("Authorization", "Bearer " + accessToken(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions quote(String items) throws Exception {
        return quote(items, null);
    }

    private ResultActions quote(String items, BranchEntity branch) throws Exception {
        String branchPart = branch == null ? "" : ",\"branchId\":\"" + branch.getId() + "\"";
        return mockMvc.perform(post("/orders/quote")
                .header("Authorization", "Bearer " + accessToken(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PICKUP\",\"items\":[" + items + "]" + branchPart + "}"));
    }

    private static String cart(MenuItemEntity dish, int quantity) {
        return item(dish, quantity);
    }

    private static String item(MenuItemEntity dish, int quantity) {
        return "{\"menuItemId\":\"" + dish.getId() + "\",\"quantity\":" + quantity + "}";
    }

    private BranchEntity branch(String code) {
        BranchEntity branch = new BranchEntity();
        branch.setCode(code + java.util.UUID.randomUUID().toString().substring(0, 8));
        branch.setName("Branch " + code);
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Offers-" + name + "-" + System.nanoTime());
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
