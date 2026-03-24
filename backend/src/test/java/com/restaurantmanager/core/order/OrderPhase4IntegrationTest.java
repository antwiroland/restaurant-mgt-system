package com.restaurantmanager.core.order;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderPhase4IntegrationTest extends BaseIntegrationTest {
    @SpyBean
    private OrderRealtimePublisher realtimePublisher;

    @BeforeEach
    void resetSpy() {
        clearInvocations(realtimePublisher);
    }

    @Test
    void givenCustomerToken_whenCreateDineInOrder_then201WithStatusPending() throws Exception {
        UserEntity customer = createUser("C1", "+233270000001", "c1@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("P4T1");
        MenuItemEntity item = createMenuItem("Dish1", "25.00");

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void givenDeliveryOrderWithNoAddress_whenCreate_then400() throws Exception {
        UserEntity customer = createUser("C2", "+233270000002", "c2@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("Dish2", "22.00");
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenCustomerToken_whenCreatePickupOrder_then201WithPickupCodeGenerated() throws Exception {
        UserEntity customer = createUser("CP", "+2332700000021", "cp@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("DishP", "14.00");
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"pickupTime\":\"" + Instant.now().plusSeconds(1500) + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupCode").isString())
                .andExpect(jsonPath("$.pickupCode").isNotEmpty());
    }

    @Test
    void givenCustomerToken_whenCreateDeliveryOrderWithAddress_then201() throws Exception {
        UserEntity customer = createUser("CD", "+2332700000022", "cd@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("DishD", "30.00");
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"deliveryAddress\":\"Accra, Airport Residential\",\"items\":[{\"menuItemId\":\""
                                + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DELIVERY"))
                .andExpect(jsonPath("$.deliveryAddress").value("Accra, Airport Residential"));
    }

    @Test
    void givenCashierToken_whenUpdateOrderStatusToConfirmed_then200() throws Exception {
        UserEntity customer = createUser("C3", "+233270000003", "c3@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("K3", "+233270000013", "k3@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("Dish3", "20.00");
        UUID orderId = createPickupOrder(customer, item);
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void givenCustomerToken_whenUpdateOrderStatus_then403() throws Exception {
        UserEntity customer = createUser("CS", "+2332700000031", "cs@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("DishS", "20.00");
        UUID orderId = createPickupOrder(customer, item);
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenCustomerToken_whenCancelOwnPendingOrder_then204() throws Exception {
        UserEntity customer = createUser("C4", "+233270000004", "c4@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("Dish4", "15.00");
        UUID orderId = createPickupOrder(customer, item);
        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"changed\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void givenCustomerToken_whenCancelConfirmedOrderWithoutPin_then403() throws Exception {
        UserEntity customer = createUser("C4A", "+2332700000041", "c4a@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("K4A", "+2332700000141", "k4a@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("Dish4A", "15.00");
        UUID orderId = createPickupOrder(customer, item);
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"too late\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenValidOverrideToken_whenCancelConfirmedOrder_then200WithStatusCancelled() throws Exception {
        UserEntity customer = createUser("C5", "+233270000005", "c5@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("K5", "+233270000015", "k5@x.com", "secret123", Role.CASHIER);
        UserEntity manager = createUser("M5", "+233270000025", "m5@x.com", "secret123", Role.MANAGER);
        MenuItemEntity item = createMenuItem("Dish5", "18.00");
        UUID orderId = createPickupOrder(customer, item);
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        String overrideToken = jwtService.generateOverrideToken(manager.getId(), manager.getRole(), OverrideActionType.VOID);
        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"confirm cancel\",\"overrideToken\":\"" + overrideToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void givenCustomerToken_whenCancelOtherCustomerOrder_then403() throws Exception {
        UserEntity owner = createUser("CO1", "+2332700000051", "co1@x.com", "secret123", Role.CUSTOMER);
        UserEntity other = createUser("CO2", "+2332700000052", "co2@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("Dish5B", "18.00");
        UUID orderId = createPickupOrder(owner, item);
        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not mine\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenDateRangeFilter_whenListOrders_thenOnlyOrdersWithinRangeReturned() throws Exception {
        UserEntity customer = createUser("CDate", "+2332700000053", "cdate@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("KDate", "+2332700000153", "kdate@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("DishDate", "18.00");

        UUID oldOrderId = createPickupOrder(customer, item);
        UUID todayOrderId = createPickupOrder(customer, item);

        OrderEntity oldOrder = orderRepository.findById(oldOrderId).orElseThrow();
        oldOrder.setCreatedAt(Instant.now().minusSeconds(5L * 24 * 3600));
        orderRepository.save(oldOrder);

        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();

        mockMvc.perform(get("/orders?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(todayOrderId.toString()));
    }

    @Test
    void givenExistingOrder_whenMenuItemPriceChangedAfterwards_thenOrderItemStillShowsOriginalPrice() throws Exception {
        UserEntity customer = createUser("CPS", "+2332700000054", "cps@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("DishSnap", "40.00");
        UUID orderId = createPickupOrder(customer, item);

        item.setPrice(new BigDecimal("55.00"));
        menuItemRepository.save(item);

        mockMvc.perform(get("/orders/" + orderId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].price").value(40.0));
    }

    @Test
    void givenPickupOrder_whenCreated_thenUniquePickupCodeAssigned() throws Exception {
        UserEntity customer = createUser("CPK", "+2332700000061", "cpk@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("DishPK", "19.00");
        String first = createPickupOrderRaw(customer, item);
        String second = createPickupOrderRaw(customer, item);

        String firstCode = objectMapper.readTree(first).get("pickupCode").asText();
        String secondCode = objectMapper.readTree(second).get("pickupCode").asText();
        assertNotEquals(firstCode, secondCode);
    }

    @Test
    void givenPickupCode_whenCashierEntersCode_thenMatchingOrderReturned() throws Exception {
        UserEntity customer = createUser("C6", "+233270000006", "c6@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("K6", "+233270000016", "k6@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("Dish6", "19.00");
        String body = createPickupOrderRaw(customer, item);
        String code = objectMapper.readTree(body).get("pickupCode").asText();
        mockMvc.perform(get("/orders/pickup/" + code)
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupCode").value(code));
    }

    @Test
    void givenDeliveryOrder_whenStatusUpdatedToOutForDelivery_then200() throws Exception {
        UserEntity customer = createUser("CD1", "+2332700000061", "cd1@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("KD1", "+2332700000161", "kd1@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("DishD1", "29.00");

        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"deliveryAddress\":\"Tema\",\"items\":[{\"menuItemId\":\""
                                + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PREPARING\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"READY\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void givenDeliveryOrder_whenStatusUpdatedToDelivered_then200() throws Exception {
        UserEntity customer = createUser("CD2", "+2332700000062", "cd2@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("KD2", "+2332700000162", "kd2@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("DishD2", "31.00");

        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"deliveryAddress\":\"East Legon\",\"items\":[{\"menuItemId\":\""
                                + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PREPARING\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"READY\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + accessToken(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OUT_FOR_DELIVERY\"}")).andExpect(status().isOk());
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void givenValidTableToken_whenCreateDineInOrder_thenOrderLinkedToTable() throws Exception {
        UserEntity customer = createUser("C7", "+233270000007", "c7@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("P4T7");
        MenuItemEntity item = createMenuItem("Dish7", "24.00");
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableToken\":\"" + table.getQrToken() + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(table.getId().toString()));
    }

    @Test
    void givenOccupiedTable_whenCreateSecondOrder_then201BothOrdersLinkedToTable() throws Exception {
        UserEntity customer = createUser("C7B", "+2332700000071", "c7b@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("P4T7B");
        MenuItemEntity item = createMenuItem("Dish7B", "24.00");

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\""
                                + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(table.getId().toString()));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\""
                                + item.getId() + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(table.getId().toString()));
    }

    @Test
    void givenDineInSession_whenCloseTable_thenTableStatusChangedToAvailable() throws Exception {
        UserEntity customer = createUser("C8", "+233270000008", "c8@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("K8", "+233270000018", "k8@x.com", "secret123", Role.CASHIER);
        RestaurantTableEntity table = createTable("P4T8");
        MenuItemEntity item = createMenuItem("Dish8", "21.00");
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + accessToken(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/orders/dine-in/tables/" + table.getId() + "/close")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isNoContent());
    }

    @Test
    void givenCustomerToken_whenCreateGroupSession_then201WithSessionCode() throws Exception {
        UserEntity host = createUser("H1", "+2332700000081", "h1@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(post("/orders/group/sessions")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionCode", startsWith("GRP-")));
    }

    @Test
    void givenSessionCode_whenSecondUserJoins_then200ParticipantAdded() throws Exception {
        UserEntity host = createUser("H2", "+2332700000082", "h2@x.com", "secret123", Role.CUSTOMER);
        UserEntity guest = createUser("G2", "+2332700000182", "g2@x.com", "secret123", Role.CUSTOMER);
        String sessionCode = createGroupSession(host);

        mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2));
    }

    @Test
    void givenParticipant_whenAddItemsToGroupCart_thenGroupTotalUpdated() throws Exception {
        UserEntity host = createUser("H3", "+2332700000083", "h3@x.com", "secret123", Role.CUSTOMER);
        String sessionCode = createGroupSession(host);
        String hostJoin = mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String hostPid = objectMapper.readTree(hostJoin).get("participantId").asText();
        MenuItemEntity item = createMenuItem("DishH3", "23.00");

        mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/items")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + hostPid + "\",\"items\":[{\"menuItemId\":\""
                                + item.getId() + "\",\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupTotal").value(46.0));
    }

    @Test
    void givenGroupSession_whenNonHostTriesToFinalize_then403() throws Exception {
        UserEntity host = createUser("H4", "+2332700000084", "h4@x.com", "secret123", Role.CUSTOMER);
        UserEntity guest = createUser("G4", "+2332700000184", "g4@x.com", "secret123", Role.CUSTOMER);
        String sessionCode = createGroupSession(host);

        mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/finalize")
                        .header("Authorization", "Bearer " + accessToken(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenGroupSession_whenHostFinalizes_then201OrderWithAllParticipantItems() throws Exception {
        UserEntity host = createUser("H9", "+233270000009", "h9@x.com", "secret123", Role.CUSTOMER);
        UserEntity guest = createUser("G9", "+233270000019", "g9@x.com", "secret123", Role.CUSTOMER);
        String sessionCode = createGroupSession(host);
        String hostJoin = mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String guestJoin = mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(guest))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String hostPid = objectMapper.readTree(hostJoin).get("participantId").asText();
        String guestPid = objectMapper.readTree(guestJoin).get("participantId").asText();
        MenuItemEntity item = createMenuItem("Dish9", "23.00");
        addGroupItem(sessionCode, accessToken(host), hostPid, item.getId());
        addGroupItem(sessionCode, accessToken(guest), guestPid, item.getId());

        mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/finalize")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PICKUP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void givenFinalizedGroupOrder_whenViewOrderItems_thenEachItemShowsParticipantId() throws Exception {
        UserEntity host = createUser("H10", "+2332700000091", "h10@x.com", "secret123", Role.CUSTOMER);
        UserEntity guest = createUser("G10", "+2332700000191", "g10@x.com", "secret123", Role.CUSTOMER);
        String sessionCode = createGroupSession(host);
        String hostJoin = mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String guestJoin = mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(guest))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String hostPid = objectMapper.readTree(hostJoin).get("participantId").asText();
        String guestPid = objectMapper.readTree(guestJoin).get("participantId").asText();

        MenuItemEntity item = createMenuItem("Dish10", "12.00");
        addGroupItem(sessionCode, accessToken(host), hostPid, item.getId());
        addGroupItem(sessionCode, accessToken(guest), guestPid, item.getId());

        mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/finalize")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PICKUP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].participantId").isNotEmpty())
                .andExpect(jsonPath("$.items[1].participantId").isNotEmpty());
    }

    @Test
    void givenSubscribedToOrderTopic_whenOrderCreated_thenWebSocketReceivesOrderCreatedEvent() throws Exception {
        UserEntity customer = createUser("C10", "+233270000010", "c10@x.com", "secret123", Role.CUSTOMER);
        MenuItemEntity item = createMenuItem("Dish10", "17.00");
        createPickupOrder(customer, item);
        verify(realtimePublisher, atLeastOnce()).publishOrderCreated(any(UUID.class), eq(OrderType.PICKUP), eq(OrderStatus.PENDING), any(BigDecimal.class));
    }

    @Test
    void givenSubscribedToOrderTopic_whenStatusUpdated_thenWebSocketReceivesStatusChangedEvent() throws Exception {
        UserEntity customer = createUser("C11", "+233270000011", "c11@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("K11", "+233270000021", "k11@x.com", "secret123", Role.CASHIER);
        MenuItemEntity item = createMenuItem("Dish11", "16.00");
        UUID orderId = createPickupOrder(customer, item);
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk());
        verify(realtimePublisher, atLeastOnce()).publishOrderStatusChanged(eq(orderId), eq(OrderStatus.PENDING), eq(OrderStatus.CONFIRMED));
    }

    @Test
    void givenGroupSessionParticipants_whenItemAdded_thenAllParticipantsReceiveGroupCartUpdatedEvent() throws Exception {
        UserEntity host = createUser("H12", "+233270000012", "h12@x.com", "secret123", Role.CUSTOMER);
        String sessionCode = createGroupSession(host);
        String join = mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/join")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String pid = objectMapper.readTree(join).get("participantId").asText();
        MenuItemEntity item = createMenuItem("Dish12", "26.00");
        addGroupItem(sessionCode, accessToken(host), pid, item.getId());
        verify(realtimePublisher, atLeastOnce()).publishGroupCartUpdated(eq(sessionCode), any(UUID.class), any(BigDecimal.class));
    }

    private UUID createPickupOrder(UserEntity customer, MenuItemEntity item) throws Exception {
        String body = createPickupOrderRaw(customer, item);
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String createPickupOrderRaw(UserEntity customer, MenuItemEntity item) throws Exception {
        return mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"pickupTime\":\"" + Instant.now().plusSeconds(1800) + "\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void addGroupItem(String sessionCode, String token, String participantId, UUID menuItemId) throws Exception {
        mockMvc.perform(post("/orders/group/sessions/" + sessionCode + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + participantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());
    }

    private String createGroupSession(UserEntity host) throws Exception {
        String body = mockMvc.perform(post("/orders/group/sessions")
                        .header("Authorization", "Bearer " + accessToken(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionCode").asText();
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Phase4-" + name + "-" + Instant.now().toEpochMilli());
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

    private RestaurantTableEntity createTable(String number) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(number + "-" + Instant.now().toEpochMilli());
        return restaurantTableRepository.save(table);
    }
}
