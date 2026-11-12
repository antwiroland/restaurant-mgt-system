package com.restaurantmanager.core.payment;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An order is paid once, and its receipt can be found from anything printed on the ticket or receipt. */
class PaymentOnceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserEntity cashier;
    private MenuItemEntity jollof;
    private String auth;

    @BeforeEach
    void setUpData() {
        cashier = createUser("Kofi", "+233300000301", "k301@x.com", "secret123", Role.CASHIER);
        auth = "Bearer " + accessToken(cashier);
        CategoryEntity category = new CategoryEntity();
        category.setName("Once-" + Instant.now().toEpochMilli());
        category.setDisplayOrder(1);
        category.setActive(true);
        category = categoryRepository.save(category);
        jollof = new MenuItemEntity();
        jollof.setCategory(category);
        jollof.setName("Jollof");
        jollof.setPrice(new BigDecimal("40.00"));
        jollof.setAvailable(true);
        jollof.setActive(true);
        jollof = menuItemRepository.save(jollof);
    }

    @Test
    void anOrderCannotBePaidTwice() throws Exception {
        UUID orderId = dineInOrder();
        JsonNode first = pay(orderId, "CASH", "cash-1", 201);
        assertThat(first.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(pay(orderId, "CASH", "cash-1", 201).get("paymentId").asText()).isEqualTo(first.get("paymentId").asText()); // same key: same payment

        mockMvc.perform(post("/payments/initiate").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderId, "CASH", "cash-2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order #" + orderId.toString().substring(0, 8).toUpperCase() + " has already been paid"));
        mockMvc.perform(post("/payments/initiate").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderId, "MOBILE_MONEY", "momo-1")))
                .andExpect(status().isConflict());
        assertThat(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).hasSize(1);
        // cash is stored once, already received: never left as INITIATED
        assertThat(paymentRepository.findAll()).allMatch(p -> p.getStatus() == PaymentStatus.SUCCESS);
    }

    @Test
    void aNewAttemptReplacesOneStillWaiting_soOnlyOnePaymentCounts() throws Exception {
        UUID orderId = dineInOrder();
        String momo = pay(orderId, "MOBILE_MONEY", "momo-1", 201).get("paymentId").asText();
        // the customer never approved the prompt: the cashier takes cash instead
        assertThat(pay(orderId, "CASH", "cash-1", 201).get("status").asText()).isEqualTo("SUCCESS");

        PaymentEntity replaced = paymentRepository.findById(UUID.fromString(momo)).orElseThrow();
        assertThat(replaced.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(replaced.getFailureReason()).isEqualTo("Replaced by a new payment attempt");
        assertThat(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).hasSize(1);
        mockMvc.perform(post("/payments/initiate").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderId, "MOBILE_MONEY", "momo-2")))
                .andExpect(status().isConflict());
    }

    @Test
    void receiptIsFoundByOrderNumberReceiptNumberReferenceOrId() throws Exception {
        UUID orderId = dineInOrder();
        JsonNode payment = pay(orderId, "CASH", "cash-1", 201);
        String paymentId = payment.get("paymentId").asText();
        String reference = payment.get("paystackReference").asText();
        String receiptNumber = json(mockMvc.perform(get("/payments/" + paymentId + "/receipt").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("receiptNumber").asText();

        for (String q : new String[]{orderId.toString().substring(0, 8).toUpperCase(), "#" + orderId.toString().substring(0, 8),
                receiptNumber, receiptNumber.toLowerCase(), reference, paymentId, orderId.toString()}) {
            mockMvc.perform(get("/payments/receipts/search").param("q", q).header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receiptNumber").value(receiptNumber));
        }
        mockMvc.perform(get("/payments/receipts/search").param("q", "CASH-NOPE1234").header("Authorization", auth))
                .andExpect(status().isNotFound());
        UUID unpaid = dineInOrder();
        mockMvc.perform(get("/payments/receipts/search").param("q", unpaid.toString().substring(0, 8)).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Receipt not found"));
        // a receipt number pasted where an id is expected is a 400, not a server error
        mockMvc.perform(get("/payments/" + receiptNumber + "/receipt").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reconciliationClosesCashThatWasNeverRecordedAsReceived() {
        UUID orderId = UUID.randomUUID();
        try {
            orderId = dineInOrder();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        PaymentEntity stuck = new PaymentEntity();
        stuck.setOrder(orderRepository.findById(orderId).orElseThrow());
        stuck.setAmount(new BigDecimal("40.00"));
        stuck.setCurrency("GHS");
        stuck.setMethod(PaymentMethod.CASH);
        stuck.setStatus(PaymentStatus.INITIATED);
        stuck.setPaystackReference("CASH-STUCK001");
        stuck.setIdempotencyKey("stuck-1");
        stuck = paymentRepository.save(stuck);
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(3600)), stuck.getId());

        paymentService.reconcilePendingPayments(15, 50);

        PaymentEntity after = paymentRepository.findById(stuck.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("Cash payment was not completed");
    }

    private JsonNode pay(UUID orderId, String method, String key, int expected) throws Exception {
        return json(mockMvc.perform(post("/payments/initiate").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderId, method, key)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private static String body(UUID orderId, String method, String key) {
        return "{\"orderId\":\"" + orderId + "\",\"method\":\"" + method + "\",\"idempotencyKey\":\"" + key + "\""
                + ("MOBILE_MONEY".equals(method) ? ",\"momoPhone\":\"0244000001\"" : ",\"amountTendered\":100") + "}";
    }

    private UUID dineInOrder() throws Exception {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber("O" + (int) (Math.random() * 100000));
        table.setCapacity(2);
        table.setZone("Main");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken("o-" + UUID.randomUUID());
        table = restaurantTableRepository.save(table);
        String body = mockMvc.perform(post("/orders").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\"" + jollof.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json(body).get("id").asText());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
