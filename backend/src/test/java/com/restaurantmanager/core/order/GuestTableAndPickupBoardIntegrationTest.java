package com.restaurantmanager.core.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.AfterCommit;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guests at a table (no account): order with a phone number, pay the whole order; the public pickup board. */
class GuestTableAndPickupBoardIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PaymentProps paymentProps;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;

    private MenuItemEntity jollof;
    private UserEntity cashier;

    @BeforeEach
    void setUpData() {
        jollof = menuItem("Jollof", "40.00");
        cashier = createUser("Cashier", "+233300000101", "cashier-guest@x.com", "secret123", Role.CASHIER);
    }

    @Test
    void guestOrdersWithPhone_withoutAccount_andTableIsNoLongerFree() throws Exception {
        RestaurantTableEntity table = table("G1");
        // a staff member has the table list open (and cached) before the guest orders
        mockMvc.perform(get("/tables").header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/public/dine-in").contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderBody(table, "Ama", "not a phone")))
                .andExpect(status().isBadRequest());

        JsonNode order = json(mockMvc.perform(post("/orders/public/dine-in").contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderBody(table, "Ama", "024 400 0001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestName").value("Ama"))
                .andExpect(jsonPath("$.guestPhone").value("0244000001"))
                .andReturn().getResponse().getContentAsString());
        assertThat(order.get("notes").asText()).isEqualTo("No pepper");

        // the guest's own tracking shows the order, its total and what is paid, but never the phone number
        String tracking = mockMvc.perform(get("/orders/public/dine-in/tables/" + table.getQrToken() + "/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(40.0))
                .andExpect(jsonPath("$[0].amountPaid").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(tracking).doesNotContain("0244000001");

        // staff see the table as occupied straight away (cache cleared on commit)
        String tables = mockMvc.perform(get("/tables").header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode t : json(tables)) {
            if (t.get("number").asText().equals("G1")) {
                assertThat(t.get("status").asText()).isEqualTo("OCCUPIED");
            }
        }
        assertThat(restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(TableStatus.OCCUPIED);
    }

    @Test
    void guestPaysWholeOrderByMomo_onceOnly_andNotWhenSplit() throws Exception {
        RestaurantTableEntity table = table("G2");
        UUID orderId = guestOrder(table);
        String base = "/orders/public/dine-in/tables/" + table.getQrToken() + "/orders/" + orderId;

        mockMvc.perform(post("/orders/public/dine-in/tables/wrong/orders/" + orderId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON).content(payBody("k-wrong")))
                .andExpect(status().isNotFound());

        String reference = payWhole(base, "k-1");
        assertThat(paymentRepository.findByPaystackReference(reference).orElseThrow().getAmount()).isEqualByComparingTo("40.00");
        assertThat(payWhole(base, "k-1")).isEqualTo(reference); // same key, same payment
        mockMvc.perform(post(base + "/pay").contentType(MediaType.APPLICATION_JSON).content(payBody("k-2")))
                .andExpect(status().isConflict()); // one already waiting for approval

        succeed(reference);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        mockMvc.perform(get("/orders/public/dine-in/tables/" + table.getQrToken() + "/tracking"))
                .andExpect(jsonPath("$[0].amountPaid").value(40.0));
        mockMvc.perform(post(base + "/pay").contentType(MediaType.APPLICATION_JSON).content(payBody("k-3")))
                .andExpect(status().isConflict()); // already paid

        // a split bill is paid share by share
        RestaurantTableEntity other = table("G3");
        UUID splitOrder = guestOrder(other);
        String otherBase = "/orders/public/dine-in/tables/" + other.getQrToken() + "/orders/" + splitOrder;
        mockMvc.perform(post(otherBase + "/splits").contentType(MediaType.APPLICATION_JSON).content("{\"parts\":2}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(otherBase + "/pay").contentType(MediaType.APPLICATION_JSON).content(payBody("k-4")))
                .andExpect(status().isConflict());
    }

    @Test
    void pickupBoard_showsOnlyPaidPickupOrderNumbersAndStatus() throws Exception {
        String pickupId = pickupOrder();
        guestOrder(table("G4")); // dine-in: not on the pickup board

        // unpaid: not the kitchen's yet
        assertThat(json(mockMvc.perform(get("/orders/public/status")).andReturn().getResponse().getContentAsString())).isEmpty();
        payCash(pickupId);

        JsonNode board = json(mockMvc.perform(get("/orders/public/status"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> numbers = new ArrayList<>();
        board.forEach(line -> numbers.add(line.get("orderNumber").asText()));
        assertThat(numbers).containsExactly(pickupId.substring(0, 8).toUpperCase());
        JsonNode line = board.get(0);
        assertThat(line.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(line.has("orderId")).isFalse();
        assertThat(line.has("tableNumber")).isFalse();
        assertThat(line.toString()).doesNotContain("shito", "Jollof", pickupId);
    }

    @Test
    void orderBoard_showsPickupAndDelivery_untilCollectedOrOutForDelivery() throws Exception {
        String pickupId = pickupOrder();
        payCash(pickupId);
        String deliveryId = json(mockMvc.perform(post("/orders").header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"deliveryAddress\":\"12 Oxford St\",\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        payCash(deliveryId);
        String pickup = pickupId.substring(0, 8).toUpperCase();
        String delivery = deliveryId.substring(0, 8).toUpperCase();

        JsonNode board = json(mockMvc.perform(get("/orders/public/status")).andReturn().getResponse().getContentAsString());
        assertThat(board).hasSize(2);
        board.forEach(line -> assertThat(line.get("type").asText()).isEqualTo(line.get("orderNumber").asText().equals(pickup) ? "PICKUP" : "DELIVERY"));

        for (String id : List.of(pickupId, deliveryId)) {
            setStatus(id, "PREPARING");
            setStatus(id, "READY");
        }
        assertThat(boardNumbers()).containsExactlyInAnyOrder(pickup, delivery);

        setStatus(deliveryId, "OUT_FOR_DELIVERY"); // the rider took it
        assertThat(boardNumbers()).containsExactly(pickup);
        setStatus(pickupId, "COMPLETED"); // the customer collected it
        assertThat(boardNumbers()).isEmpty();
    }

    private void setStatus(String orderId, String next) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> boardNumbers() throws Exception {
        List<String> numbers = new ArrayList<>();
        json(mockMvc.perform(get("/orders/public/status")).andReturn().getResponse().getContentAsString())
                .forEach(line -> numbers.add(line.get("orderNumber").asText()));
        return numbers;
    }

    @Test
    void pickupIsPaidBeforeTheKitchenSeesIt() throws Exception {
        String pickupId = pickupOrder();
        assertThat(kdsOrderIds()).doesNotContain(pickupId);

        // staff cannot push an unpaid pickup to the kitchen
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/orders/" + pickupId + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict());

        payCash(pickupId);
        assertThat(orderRepository.findById(UUID.fromString(pickupId)).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(kdsOrderIds()).contains(pickupId);

        // dine-in still goes straight to the kitchen and is paid at the end
        UUID dineIn = guestOrder(table("G5"));
        assertThat(kdsOrderIds()).contains(dineIn.toString());
    }

    @Test
    void unpaidPickup_isCancelledAfterTheWindow_unlessAPaymentIsUnderway() throws Exception {
        String abandoned = pickupOrder();
        String paying = pickupOrder();
        String fresh = pickupOrder();
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.from(Instant.now().minusSeconds(45 * 60)), UUID.fromString(abandoned), UUID.fromString(paying));
        mockMvc.perform(post("/payments/initiate").header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + paying + "\",\"method\":\"MOBILE_MONEY\",\"momoPhone\":\"0244000001\",\"idempotencyKey\":\"momo-" + paying + "\"}"))
                .andExpect(status().isCreated());

        assertThat(orderService.expireUnpaidPickups(30)).isEqualTo(1);
        OrderEntity cancelled = orderRepository.findById(UUID.fromString(abandoned)).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Not paid within 30 minutes");
        assertThat(orderRepository.findById(UUID.fromString(paying)).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderRepository.findById(UUID.fromString(fresh)).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    private String pickupOrder() throws Exception {
        String body = mockMvc.perform(post("/orders").header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}],\"notes\":\"Extra shito\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json(body).get("id").asText();
    }

    private void payCash(String orderId) throws Exception {
        mockMvc.perform(post("/payments/initiate").header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"method\":\"CASH\",\"idempotencyKey\":\"cash-" + orderId + "\",\"amountTendered\":50}"))
                .andExpect(status().isCreated());
    }

    private List<String> kdsOrderIds() throws Exception {
        JsonNode columns = json(mockMvc.perform(get("/kds/board").header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("columns");
        List<String> ids = new ArrayList<>();
        columns.forEach(column -> column.forEach(card -> ids.add(card.get("orderId").asText())));
        return ids;
    }

    @Test
    void staffFindAnOrderByTheShortNumberOnTheTicket() throws Exception {
        String orderId = pickupOrder();
        String number = orderId.substring(0, 8).toUpperCase();
        String auth = "Bearer " + accessToken(cashier);

        mockMvc.perform(get("/orders/lookup?number=" + number).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId));
        mockMvc.perform(get("/orders/lookup").param("number", "#" + number.toLowerCase()).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId)); // "#7d63d7f5" works too
        mockMvc.perform(get("/orders/lookup?number=" + orderId).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId));
        mockMvc.perform(get("/orders/lookup?number=ZZ12").header("Authorization", auth)).andExpect(status().isBadRequest());
        String other = number.charAt(0) == 'F' ? "E" : "F";
        mockMvc.perform(get("/orders/lookup?number=" + other + number.substring(1)).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/orders/lookup?number=" + number)).andExpect(status().isUnauthorized());
    }

    @Test
    void afterCommit_defersWorkUntilTheTransactionCommits() {
        List<String> ran = new ArrayList<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AfterCommit.run(() -> ran.add("event"));
            assertThat(ran).isEmpty();
        });
        assertThat(ran).containsExactly("event");

        ran.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AfterCommit.run(() -> ran.add("event"));
            status.setRollbackOnly();
        });
        assertThat(ran).isEmpty();

        AfterCommit.run(() -> ran.add("now")); // no transaction: straight away
        assertThat(ran).containsExactly("now");
    }

    private String guestOrderBody(RestaurantTableEntity table, String name, String phone) {
        return "{\"tableToken\":\"" + table.getQrToken() + "\",\"items\":[{\"menuItemId\":\"" + jollof.getId()
                + "\",\"quantity\":1}],\"notes\":\"No pepper\",\"guestName\":\"" + name + "\",\"guestPhone\":\"" + phone + "\"}";
    }

    private UUID guestOrder(RestaurantTableEntity table) throws Exception {
        String body = mockMvc.perform(post("/orders/public/dine-in").contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderBody(table, "Kofi", "+233 24 400 0002")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestPhone").value("+233244000002"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json(body).get("id").asText());
    }

    private String payWhole(String base, String key) throws Exception {
        String body = mockMvc.perform(post(base + "/pay").contentType(MediaType.APPLICATION_JSON).content(payBody(key)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("paystackReference").asText();
    }

    private static String payBody(String key) {
        return "{\"momoPhone\":\"0244000001\",\"idempotencyKey\":\"" + key + "\"}";
    }

    private void succeed(String reference) throws Exception {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt-" + reference + "\",\"status\":\"success\",\"reference\":\"" + reference + "\"}}";
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(paymentProps.getPaystack().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post("/payments/webhook").header("X-Paystack-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private RestaurantTableEntity table(String number) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(number.toLowerCase() + "-" + UUID.randomUUID());
        return restaurantTableRepository.save(table);
    }

    private MenuItemEntity menuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Guest-" + name + "-" + Instant.now().toEpochMilli());
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
