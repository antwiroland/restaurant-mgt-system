package com.restaurantmanager.core.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.printing.PrinterTransport;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Restaurant profile, offline cash, item-level KDS, service requests, printing, push devices, favourites. */
class OperationsFeaturesIntegrationTest extends BaseIntegrationTest {
    @MockBean
    private PrinterTransport printerTransport;

    private UserEntity admin;
    private UserEntity cashier;
    private UserEntity customer;
    private MenuItemEntity jollof;
    private MenuItemEntity drink;
    private RestaurantTableEntity table;

    @BeforeEach
    void setUp() {
        admin = createUser("Admin", "+233270000001", "admin@ops.test", "secret123", Role.ADMIN);
        cashier = createUser("Cashier", "+233270000002", null, "secret123", Role.CASHIER);
        customer = createUser("Customer", "+233270000003", null, "secret123", Role.CUSTOMER);
        CategoryEntity category = new CategoryEntity();
        category.setName("Ops " + UUID.randomUUID());
        category.setDisplayOrder(1);
        category.setActive(true);
        category = categoryRepository.save(category);
        jollof = item(category, "Jollof", "80.00");
        drink = item(category, "Sobolo", "20.00");
        table = new RestaurantTableEntity();
        table.setNumber("OPS1");
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken("ops-" + UUID.randomUUID());
        table = restaurantTableRepository.save(table);
    }

    /* --------------------------------------------------------------- profile */

    @Test
    void givenAdmin_whenProfileUpdated_thenPublicReadReturnsIt() throws Exception {
        mockMvc.perform(put("/restaurant/profile").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hearth","tagline":"Seasonal kitchen","address":"12 Oxford St, Accra",
                                 "phone":"+233300000000","currency":"ghs","openingHours":["Mon-Fri 11:00-22:00"," ","Sat 10:00-23:00"],
                                 "latitude":5.556,"longitude":-0.196}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/restaurant/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hearth"))
                .andExpect(jsonPath("$.currency").value("GHS"))
                .andExpect(jsonPath("$.openingHours.length()").value(2))
                .andExpect(jsonPath("$.latitude").value(5.556));
    }

    @Test
    void givenCashier_whenProfileUpdated_thenForbidden() throws Exception {
        mockMvc.perform(put("/restaurant/profile").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    /* --------------------------------------------------------------- cash */

    @Test
    void givenCashTendered_whenPaid_thenRecordedImmediatelyWithChangeAndOrderConfirmed() throws Exception {
        UUID orderId = staffPickupOrder();
        String body = mockMvc.perform(post("/payments/initiate").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"method\":\"CASH\",\"idempotencyKey\":\"cash-%s\",\"amountTendered\":200}".formatted(orderId, orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertEquals(0, new BigDecimal("100.00").compareTo(json.get("changeDue").decimalValue()));
        assertEquals(OrderStatus.CONFIRMED, orderRepository.findById(orderId).orElseThrow().getStatus());
        var payment = paymentRepository.findById(UUID.fromString(json.get("paymentId").asText())).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(cashier.getId(), payment.getReceivedBy());
    }

    @Test
    void givenCashBelowAmountDue_whenPaid_then400() throws Exception {
        UUID orderId = staffPickupOrder();
        mockMvc.perform(post("/payments/initiate").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"method\":\"CASH\",\"idempotencyKey\":\"low-%s\",\"amountTendered\":10}".formatted(orderId, orderId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenMobileMoneyWithoutPhone_whenPaid_then400() throws Exception {
        UUID orderId = staffPickupOrder();
        mockMvc.perform(post("/payments/initiate").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"method\":\"MOBILE_MONEY\",\"idempotencyKey\":\"momo-%s\"}".formatted(orderId, orderId)))
                .andExpect(status().isBadRequest());
    }

    /* --------------------------------------------------------------- item-level KDS */

    @Test
    void givenAcceptedOrder_whenEveryItemMarkedReady_thenOrderMovesToReady() throws Exception {
        UUID orderId = staffPickupOrder();
        payCash(orderId);
        JsonNode order = objectMapper.readTree(mockMvc.perform(get("/orders/" + orderId).header("Authorization", bearer(cashier)))
                .andReturn().getResponse().getContentAsString());
        String first = order.get("items").get(0).get("id").asText();
        String second = order.get("items").get(1).get("id").asText();

        markReady(orderId, first, true).andExpect(jsonPath("$.status").value("PREPARING"));
        mockMvc.perform(get("/kds/board").header("Authorization", bearer(cashier)))
                .andExpect(jsonPath("$.columns.PREPARING[0].items[?(@.ready == true)]").isNotEmpty());
        markReady(orderId, second, true).andExpect(jsonPath("$.status").value("READY"));
        assertEquals(OrderStatus.READY, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void givenUnpaidPendingOrder_whenItemMarkedReady_then409() throws Exception {
        UUID orderId = staffPickupOrder();
        String itemId = orderItemRepository.findByOrderIdIn(java.util.List.of(orderId)).get(0).getId().toString();
        markReady(orderId, itemId, true).andExpect(status().isConflict());
    }

    /* --------------------------------------------------------------- service requests */

    @Test
    void givenGuestCallsWaiter_whenStaffResolve_thenStatusFollowsAndDuplicatesAreMerged() throws Exception {
        String token = table.getQrToken();
        String first = mockMvc.perform(post("/tables/public/" + token + "/requests")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CALL_WAITER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("OPS1"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(first).get("id").asText();
        // Second tap within the window returns the same request
        mockMvc.perform(post("/tables/public/" + token + "/requests")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CALL_WAITER\"}"))
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/service-requests").header("Authorization", bearer(cashier)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
        mockMvc.perform(patch("/service-requests/" + id).header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
        mockMvc.perform(get("/tables/public/" + token + "/requests"))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
        mockMvc.perform(get("/service-requests").header("Authorization", bearer(cashier)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void givenUnknownTableToken_whenRequestMade_then404() throws Exception {
        mockMvc.perform(post("/tables/public/nope/requests")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BILL\"}"))
                .andExpect(status().isNotFound());
    }

    /* --------------------------------------------------------------- printing */

    @Test
    void givenReceiptPrinter_whenReceiptPrinted_thenEscPosSentToPrinter() throws Exception {
        String printer = mockMvc.perform(post("/printers").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Counter\",\"kind\":\"RECEIPT\",\"host\":\"10.0.0.50\",\"paperWidthMm\":58}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.port").value(9100))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = staffPickupOrder();
        payCash(orderId);

        mockMvc.perform(post("/orders/" + orderId + "/print/receipt").header("Authorization", bearer(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.printerName").value("Counter"));
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(printerTransport).send(eq("10.0.0.50"), eq(9100), bytes.capture());
        String text = new String(bytes.getValue(), StandardCharsets.US_ASCII);
        assertTrue(text.contains("Jollof"), "receipt lists the dish");
        assertTrue(text.contains("TOTAL"), "receipt has a total line");
        assertTrue(text.lines().allMatch(line -> line.replaceAll("[\\x00-\\x1F]", "").length() <= 40),
                "58 mm receipts stay within 32 columns plus control bytes");
        assertTrue(objectMapper.readTree(printer).get("id").asText().length() > 0);
    }

    @Test
    void givenNoKitchenPrinter_whenTicketPrinted_then409() throws Exception {
        UUID orderId = staffPickupOrder();
        mockMvc.perform(post("/orders/" + orderId + "/print/kitchen").header("Authorization", bearer(cashier)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenUnreachablePrinter_whenPrinted_then502() throws Exception {
        doThrow(new IOException("connect timed out")).when(printerTransport).send(any(), anyInt(), any());
        mockMvc.perform(post("/printers").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pass\",\"kind\":\"KITCHEN\",\"host\":\"10.0.0.51\"}"))
                .andExpect(status().isCreated());
        UUID orderId = staffPickupOrder();
        mockMvc.perform(post("/orders/" + orderId + "/print/kitchen").header("Authorization", bearer(cashier)))
                .andExpect(status().isBadGateway());
    }

    /* --------------------------------------------------------------- push devices */

    @Test
    void givenCustomer_whenDeviceRegisteredTwice_thenStoredOnceAndRemovable() throws Exception {
        String token = "ExponentPushToken[abc123]";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/notifications/devices").header("Authorization", bearer(customer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"%s\",\"platform\":\"android\"}".formatted(token)))
                    .andExpect(status().isNoContent());
        }
        assertEquals(1, pushDeviceRepository.count());
        mockMvc.perform(post("/notifications/devices").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"not-a-token\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/notifications/devices/" + token).header("Authorization", bearer(customer)))
                .andExpect(status().isNoContent());
        assertEquals(0, pushDeviceRepository.count());
    }

    /* --------------------------------------------------------------- favourites */

    @Test
    void givenCustomer_whenFavouritesToggled_thenListReflectsIt() throws Exception {
        mockMvc.perform(put("/users/me/favourites/" + jollof.getId()).header("Authorization", bearer(customer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/users/me/favourites/" + jollof.getId()).header("Authorization", bearer(customer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/users/me/favourites/" + drink.getId()).header("Authorization", bearer(customer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/users/me/favourites").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Sobolo"));
        mockMvc.perform(delete("/users/me/favourites/" + drink.getId()).header("Authorization", bearer(customer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/users/me/favourites").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/users/me/favourites").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
    }

    /* --------------------------------------------------------------- helpers */

    private String bearer(UserEntity user) {
        return "Bearer " + accessToken(user);
    }

    private MenuItemEntity item(CategoryEntity category, String name, String price) {
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(category);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }

    private UUID staffPickupOrder() throws Exception {
        String body = mockMvc.perform(post("/orders").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"%s\",\"quantity\":1},{\"menuItemId\":\"%s\",\"quantity\":1}]}"
                                .formatted(jollof.getId(), drink.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void payCash(UUID orderId) throws Exception {
        mockMvc.perform(post("/payments/initiate").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"method\":\"CASH\",\"idempotencyKey\":\"pay-%s\"}".formatted(orderId, orderId)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions markReady(UUID orderId, String itemId, boolean ready) throws Exception {
        return mockMvc.perform(patch("/orders/" + orderId + "/items/" + itemId + "/ready").header("Authorization", bearer(cashier))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ready\":" + ready + "}"));
    }
}
