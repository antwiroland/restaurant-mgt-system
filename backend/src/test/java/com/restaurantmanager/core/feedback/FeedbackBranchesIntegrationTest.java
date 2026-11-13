package com.restaurantmanager.core.feedback;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppService;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Feedback per branch, attributed to rider and staff, the customer rating window and guest replies. */
class FeedbackBranchesIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BranchRepository branchRepository;
    @SpyBean
    private WhatsAppService whatsAppService;

    private BranchEntity osu;
    private BranchEntity legon;
    private UserEntity admin;
    private UserEntity osuManager;
    private UserEntity cashier;
    private UserEntity rider;
    private UserEntity customer;
    private MenuItemEntity jollof;

    @BeforeEach
    void setUpData() {
        osu = branch("OSU");
        legon = branch("LEG");
        admin = createUser("Admin", "+233330000001", "fb-admin@x.com", "secret123", Role.ADMIN);
        osuManager = createUser("Osu Mgr", "+233330000002", "fb-mgr@x.com", "secret123", Role.MANAGER);
        cashier = createUser("Kofi", "+233330000003", "fb-cash@x.com", "secret123", Role.CASHIER);
        rider = createUser("Kwame", "+233330000004", "fb-rider@x.com", "secret123", Role.RIDER);
        customer = createUser("Ama", "+233330000005", "fb-cust@x.com", "secret123", Role.CUSTOMER);
        jollof = menuItem();
    }

    @Test
    void branchManagerSeesAndAnswersOnlyTheirBranch_adminCanFilter() throws Exception {
        UUID osuFeedback = rate(finishedOrder(osu, null, null, Instant.now()), "{\"rating\":2,\"comment\":\"Cold\"}");
        UUID legonFeedback = rate(finishedOrder(legon, null, null, Instant.now()), "{\"rating\":5}");

        mockMvc.perform(get("/feedback").header("Authorization", managerToken()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(osuFeedback.toString()))
                .andExpect(jsonPath("$[0].branchName").value("Branch OSU"));
        mockMvc.perform(get("/feedback/summary").header("Authorization", managerToken()))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.lowRatings").value(1));
        mockMvc.perform(respond(legonFeedback, managerToken())).andExpect(status().isForbidden());
        mockMvc.perform(respond(osuFeedback, managerToken())).andExpect(status().isOk());

        // Ratings of orders without a branch are a shared queue: every branch's managers see and can answer them.
        UUID shared = rate(finishedOrder(null, null, null, Instant.now()), "{\"rating\":4}");
        mockMvc.perform(get("/feedback").header("Authorization", managerToken()))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(respond(shared, managerToken())).andExpect(status().isOk());

        mockMvc.perform(get("/feedback").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/feedback").param("branchId", legon.getId().toString()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(legonFeedback.toString()));
    }

    @Test
    void ratingsAreAttributedToRiderStaffAndDish() throws Exception {
        rate(finishedOrder(osu, cashier, rider, Instant.now()), "{\"rating\":4,\"foodRating\":5,\"serviceRating\":3,\"deliveryRating\":2}");
        rate(finishedOrder(osu, cashier, rider, Instant.now()), "{\"rating\":5,\"foodRating\":3,\"serviceRating\":5,\"deliveryRating\":4}");

        mockMvc.perform(get("/feedback").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[0].riderName").value("Kwame"))
                .andExpect(jsonPath("$[0].staffName").value("Kofi"));
        mockMvc.perform(get("/feedback/breakdown").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riders[0].name").value("Kwame"))
                .andExpect(jsonPath("$.riders[0].count").value(2))
                .andExpect(jsonPath("$.riders[0].average").value(3.0))      // delivery 2 and 4
                .andExpect(jsonPath("$.staff[0].name").value("Kofi"))
                .andExpect(jsonPath("$.staff[0].average").value(4.0))       // service 3 and 5
                .andExpect(jsonPath("$.dishes[0].name").value("Jollof"))
                .andExpect(jsonPath("$.dishes[0].averageFood").value(4.0)); // food 5 and 3
    }

    @Test
    void signedInCustomer_cannotRateLongAfterTheOrder() throws Exception {
        UUID old = finishedOrder(osu, null, null, Instant.now().minus(20, ChronoUnit.DAYS));
        mockMvc.perform(post("/orders/" + old + "/feedback")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":3}"))
                .andExpect(status().isGone());
    }

    @Test
    void guestWhoLeavesAPhone_getsTheReply_andSeesItOnTheTablePage() throws Exception {
        RestaurantTableEntity table = table();
        String body = mockMvc.perform(post("/orders/public/dine-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + table.getQrToken() + "\",\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.COMPLETED);
        order.setServedAt(Instant.now());
        orderRepository.save(order);

        String feedback = mockMvc.perform(post("/orders/public/dine-in/tables/" + table.getQrToken() + "/orders/" + orderId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":2,\"comment\":\"Slow\",\"guestName\":\"Efua\",\"guestPhone\":\"+233 24 000 1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canReceiveReply").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID feedbackId = UUID.fromString(objectMapper.readTree(feedback).get("id").asText());

        mockMvc.perform(respond(feedbackId, bearer(admin))).andExpect(status().isOk());
        verify(whatsAppService).sendStatusUpdate(eq("+233240001234"), contains("Sorry"));
        mockMvc.perform(get("/orders/public/dine-in/tables/" + table.getQrToken() + "/orders/" + orderId + "/rating"))
                .andExpect(jsonPath("$.response").value("Sorry about that, your next drink is on us."));
    }

    private org.springframework.test.web.servlet.RequestBuilder respond(UUID feedbackId, String auth) {
        return post("/feedback/" + feedbackId + "/respond")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":\"Sorry about that, your next drink is on us.\"}");
    }

    /** A customer's pickup order at a branch, finished (COMPLETED) at {@code finishedAt}. */
    private UUID finishedOrder(BranchEntity branch, UserEntity takenBy, UserEntity deliveredBy, Instant finishedAt) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + (deliveredBy == null ? "PICKUP" : "DELIVERY") + "\""
                                + (branch == null ? "" : ",\"branchId\":\"" + branch.getId() + "\"")
                                + (deliveredBy == null ? "" : ",\"deliveryAddress\":\"1 Ring Rd\"")
                                + ",\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        OrderEntity order = orderRepository.findById(id).orElseThrow();
        order.setStatus(deliveredBy == null ? OrderStatus.COMPLETED : OrderStatus.DELIVERED);
        order.setServedAt(finishedAt);
        if (deliveredBy != null) {
            order.setRiderUserId(deliveredBy.getId());
            order.setDeliveredAt(finishedAt);
        }
        if (takenBy != null) order.setCreatedByUserId(takenBy.getId());
        orderRepository.save(order);
        return id;
    }

    private UUID rate(UUID orderId, String json) throws Exception {
        ResultActions result = mockMvc.perform(post("/orders/" + orderId + "/feedback")
                .header("Authorization", bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content(json));
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String managerToken() {
        return "Bearer " + jwtService.generateAccessToken(osuManager.getId(), Role.MANAGER, osu.getId());
    }

    private String bearer(UserEntity user) {
        return "Bearer " + accessToken(user);
    }

    private BranchEntity branch(String code) {
        BranchEntity branch = new BranchEntity();
        branch.setCode(code + UUID.randomUUID().toString().substring(0, 8));
        branch.setName("Branch " + code);
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private RestaurantTableEntity table() {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber("FB" + UUID.randomUUID().toString().substring(0, 6));
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken("fb-" + UUID.randomUUID());
        return restaurantTableRepository.save(table);
    }

    private MenuItemEntity menuItem() {
        CategoryEntity category = new CategoryEntity();
        category.setName("FB-" + UUID.randomUUID());
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity saved = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(saved);
        item.setName("Jollof");
        item.setPrice(new BigDecimal("50.00"));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}
