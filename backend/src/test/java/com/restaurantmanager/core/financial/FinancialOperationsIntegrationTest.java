package com.restaurantmanager.core.financial;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialOperationsIntegrationTest extends BaseIntegrationTest {
    private UserEntity manager;
    private UserEntity cashier;
    private MenuItemEntity burger;

    @BeforeEach
    void setUpData() {
        manager = createUser("Manager", "+233240000001", "fin-manager@x.com", "secret123", Role.MANAGER);
        cashier = createUser("Cashier", "+233240000002", "fin-cashier@x.com", "secret123", Role.CASHIER);
        burger = createMenuItem("Burger", "50.00");
    }

    // ------------------------------------------------------------ discount

    @Test
    void givenBlankOverrideToken_whenDiscount_then400() throws Exception {
        UUID orderId = createPickupOrder(2);
        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrideToken\":\"\",\"orderId\":\"" + orderId + "\",\"mode\":\"FLAT\",\"value\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenTokenForOtherAction_whenDiscount_then403() throws Exception {
        UUID orderId = createPickupOrder(2);
        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountBody(override(OverrideActionType.REFUND), orderId, "FLAT", "5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenFlatDiscount_whenApplied_thenOrderTotalReducedAndAudited() throws Exception {
        UUID orderId = createPickupOrder(2);

        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountBody(override(OverrideActionType.DISCOUNT), orderId, "FLAT", "15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(15.0))
                .andExpect(jsonPath("$.newTotal").value(85.0))
                .andExpect(jsonPath("$.approverUserId").value(manager.getId().toString()));

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(0, new BigDecimal("85.00").compareTo(order.getTotal()));
        assertEquals(0, new BigDecimal("15.00").compareTo(order.getManualDiscount()));
        assertEquals(1, auditLogRepository.findByAction(AuditAction.DISCOUNT_APPLIED).size());
    }

    @Test
    void givenPercentageDiscount_whenApplied_thenOrderTotalReducedByPercentage() throws Exception {
        UUID orderId = createPickupOrder(2);

        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountBody(override(OverrideActionType.DISCOUNT), orderId, "PERCENTAGE", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(10.0))
                .andExpect(jsonPath("$.newTotal").value(90.0));
    }

    @Test
    void givenDiscountGreaterThanTotal_whenApplied_then400() throws Exception {
        UUID orderId = createPickupOrder(1);
        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountBody(override(OverrideActionType.DISCOUNT), orderId, "FLAT", "51")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenPaidOrder_whenDiscountApplied_then409() throws Exception {
        UUID orderId = createPickupOrder(1);
        createPayment(orderId, PaymentStatus.SUCCESS, PaymentMethod.MOBILE_MONEY);
        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountBody(override(OverrideActionType.DISCOUNT), orderId, "FLAT", "5")))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ void

    @Test
    void givenUnpaidOrder_whenVoided_thenCancelledOpenPaymentsVoidedAndAudited() throws Exception {
        UUID orderId = createPickupOrder(1);
        PaymentEntity pending = createPayment(orderId, PaymentStatus.PENDING, PaymentMethod.MOBILE_MONEY);

        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voidBody(override(OverrideActionType.VOID), orderId, "Customer left")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentsVoided").value(1));

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getVoidedAt());
        assertEquals(manager.getId(), order.getVoidedBy());
        assertEquals(PaymentStatus.VOIDED, paymentRepository.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(1, auditLogRepository.findByAction(AuditAction.ORDER_VOIDED).size());
    }

    @Test
    void givenDineInOrder_whenVoided_thenTableReleased() throws Exception {
        RestaurantTableEntity table = createTable("F1");
        UUID orderId = createDineInOrder(table);
        assertEquals(TableStatus.OCCUPIED, restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voidBody(override(OverrideActionType.VOID), orderId, null)))
                .andExpect(status().isOk());

        assertEquals(TableStatus.AVAILABLE, restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus());
    }

    @Test
    void givenPaidOrder_whenVoided_then409() throws Exception {
        UUID orderId = createPickupOrder(1);
        createPayment(orderId, PaymentStatus.SUCCESS, PaymentMethod.MOBILE_MONEY);
        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voidBody(override(OverrideActionType.VOID), orderId, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenCompletedOrder_whenVoided_then400() throws Exception {
        UUID orderId = createPickupOrder(1);
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voidBody(override(OverrideActionType.VOID), orderId, null)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ refund

    @Test
    void givenSuccessfulPayment_whenFullyRefunded_thenPaymentRefundedAndAudited() throws Exception {
        UUID orderId = createPickupOrder(2);
        PaymentEntity payment = createPayment(orderId, PaymentStatus.SUCCESS, PaymentMethod.MOBILE_MONEY);

        mockMvc.perform(post("/financial/refund")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(override(OverrideActionType.REFUND), payment.getId(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.0))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.remainingRefundable").value(0.0));

        PaymentEntity refreshed = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, refreshed.getStatus());
        assertEquals(1, paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(payment.getId()).size());
        assertEquals(1, auditLogRepository.findByAction(AuditAction.PAYMENT_REFUNDED).size());
    }

    @Test
    void givenSuccessfulPayment_whenPartiallyRefunded_thenRemainingRefundableTracked() throws Exception {
        UUID orderId = createPickupOrder(2);
        PaymentEntity payment = createPayment(orderId, PaymentStatus.SUCCESS, PaymentMethod.MOBILE_MONEY);

        mockMvc.perform(post("/financial/refund")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(override(OverrideActionType.REFUND), payment.getId(), "30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.remainingRefundable").value(70.0));

        mockMvc.perform(post("/financial/refund")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(override(OverrideActionType.REFUND), payment.getId(), "71")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenRefundedPayment_whenRefundedAgain_then400() throws Exception {
        UUID orderId = createPickupOrder(1);
        PaymentEntity payment = createPayment(orderId, PaymentStatus.SUCCESS, PaymentMethod.CASH);
        String token = override(OverrideActionType.REFUND);
        mockMvc.perform(post("/financial/refund")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(token, payment.getId(), null)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/financial/refund")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(token, payment.getId(), null)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ reconciliation & audit

    @Test
    void givenDayActivity_whenReconciled_thenTotalsComputedFromLedgerAndSignOffPersisted() throws Exception {
        UUID paidOrder = createPickupOrder(2);
        PaymentEntity cash = createPayment(paidOrder, PaymentStatus.SUCCESS, PaymentMethod.CASH);
        mockMvc.perform(post("/financial/refund")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(override(OverrideActionType.REFUND), cash.getId(), "20")))
                .andExpect(status().isOk());
        UUID voidedOrder = createPickupOrder(1);
        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voidBody(override(OverrideActionType.VOID), voidedOrder, null)))
                .andExpect(status().isOk());

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(get("/financial/reconciliation/" + today)
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossSales").value(100.0))
                .andExpect(jsonPath("$.totalRefunds").value(20.0))
                .andExpect(jsonPath("$.totalVoids").value(50.0))
                .andExpect(jsonPath("$.netSales").value(80.0))
                .andExpect(jsonPath("$.expectedCash").value(80.0))
                .andExpect(jsonPath("$.signOff").doesNotExist());

        String signOffBody = "{\"overrideToken\":\"" + override(OverrideActionType.DISCOUNT) + "\",\"countedCash\":75}";
        mockMvc.perform(post("/financial/reconciliation/" + today + "/sign-off")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signOffBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signOff.cashVariance").value(-5.0));
        assertEquals(1, reconciliationSignOffRepository.count());

        mockMvc.perform(post("/financial/reconciliation/" + today + "/sign-off")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signOffBody))
                .andExpect(status().isConflict());
    }

    @Test
    void givenCashier_whenQueryFinancialAudit_then403() throws Exception {
        mockMvc.perform(get("/financial/audit")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenManager_whenQueryFinancialAudit_thenPersistedEventsReturned() throws Exception {
        UUID orderId = createPickupOrder(1);
        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voidBody(override(OverrideActionType.VOID), orderId, "test")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/financial/audit?action=ORDER_VOIDED")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].actorId").value(manager.getId().toString()))
                .andExpect(jsonPath("$[0].entityId").value(orderId.toString()));
    }

    // ------------------------------------------------------------ helpers

    private String override(OverrideActionType type) {
        return jwtService.generateOverrideToken(manager.getId(), Role.MANAGER, type);
    }

    private String discountBody(String token, UUID orderId, String mode, String value) {
        return "{\"overrideToken\":\"%s\",\"orderId\":\"%s\",\"mode\":\"%s\",\"value\":%s}".formatted(token, orderId, mode, value);
    }

    private String voidBody(String token, UUID orderId, String reason) {
        return reason == null
                ? "{\"overrideToken\":\"%s\",\"orderId\":\"%s\"}".formatted(token, orderId)
                : "{\"overrideToken\":\"%s\",\"orderId\":\"%s\",\"reason\":\"%s\"}".formatted(token, orderId, reason);
    }

    private String refundBody(String token, UUID paymentId, String amount) {
        return amount == null
                ? "{\"overrideToken\":\"%s\",\"paymentId\":\"%s\"}".formatted(token, paymentId)
                : "{\"overrideToken\":\"%s\",\"paymentId\":\"%s\",\"amount\":%s}".formatted(token, paymentId, amount);
    }

    private UUID createPickupOrder(int quantity) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + burger.getId() + "\",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createDineInOrder(RestaurantTableEntity table) throws Exception {
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DINE_IN\",\"tableId\":\"" + table.getId() + "\",\"items\":[{\"menuItemId\":\"" + burger.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private PaymentEntity createPayment(UUID orderId, PaymentStatus status, PaymentMethod method) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        PaymentEntity payment = new PaymentEntity();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setCurrency("GHS");
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setPaystackReference("REF-" + UUID.randomUUID());
        payment.setIdempotencyKey("IDEM-" + UUID.randomUUID());
        if (status == PaymentStatus.SUCCESS) {
            payment.setPaidAt(Instant.now());
        }
        PaymentEntity saved = paymentRepository.save(payment);
        assertTrue(saved.getId() != null);
        return saved;
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
        category.setName("Fin-" + name + "-" + Instant.now().toEpochMilli());
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
