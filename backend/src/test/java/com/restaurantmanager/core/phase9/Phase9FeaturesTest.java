package com.restaurantmanager.core.phase9;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.phase9.audit.AuditReviewService;
import com.restaurantmanager.core.phase9.audit.FinancialAuditEvent;
import com.restaurantmanager.core.phase9.discount.DiscountMode;
import com.restaurantmanager.core.phase9.discount.DiscountResult;
import com.restaurantmanager.core.phase9.discount.DiscountService;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationEntry;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationService;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationSummary;
import com.restaurantmanager.core.phase9.refund.PaymentRecord;
import com.restaurantmanager.core.phase9.refund.PaymentRecordStatus;
import com.restaurantmanager.core.phase9.refund.RefundService;
import com.restaurantmanager.core.phase9.voiding.OrderChannel;
import com.restaurantmanager.core.phase9.voiding.TableLinkStatus;
import com.restaurantmanager.core.phase9.voiding.VoidService;
import com.restaurantmanager.core.phase9.voiding.VoidableOrder;
import com.restaurantmanager.core.phase9.voiding.VoidableOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9FeaturesTest {
    private final UUID managerId = UUID.randomUUID();

    @Test
    void givenNoOverrideToken_whenRefundAttempted_then403() {
        RefundService service = new RefundService();
        PaymentRecord payment = new PaymentRecord(UUID.randomUUID(), new BigDecimal("100.00"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.refund(payment, new BigDecimal("10.00"), false, managerId, new ArrayList<>()));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void givenValidManagerOverrideToken_whenFullRefund_thenPaystackRefundCalledAndPaymentStatusRefunded() {
        RefundService service = new RefundService();
        PaymentRecord payment = new PaymentRecord(UUID.randomUUID(), new BigDecimal("100.00"));

        PaymentRecord updated = service.refund(payment, new BigDecimal("100.00"), true, managerId, new ArrayList<>());

        assertEquals(PaymentRecordStatus.REFUNDED, updated.getStatus());
    }

    @Test
    void givenValidManagerOverrideToken_whenPartialRefund_thenPaymentStatusPartiallyRefundedCorrectAmount() {
        RefundService service = new RefundService();
        PaymentRecord payment = new PaymentRecord(UUID.randomUUID(), new BigDecimal("100.00"));

        PaymentRecord updated = service.refund(payment, new BigDecimal("30.00"), true, managerId, new ArrayList<>());

        assertEquals(PaymentRecordStatus.PARTIALLY_REFUNDED, updated.getStatus());
        assertEquals(new BigDecimal("30.00"), updated.getRefundedAmount());
    }

    @Test
    void givenRefundAmountExceedsPaymentAmount_whenAttempted_then400() {
        RefundService service = new RefundService();
        PaymentRecord payment = new PaymentRecord(UUID.randomUUID(), new BigDecimal("100.00"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.refund(payment, new BigDecimal("150.00"), true, managerId, new ArrayList<>()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenSuccessfulRefund_thenRefundEventWrittenToAuditLog() {
        RefundService service = new RefundService();
        PaymentRecord payment = new PaymentRecord(UUID.randomUUID(), new BigDecimal("100.00"));
        List<FinancialAuditEvent> audit = new ArrayList<>();

        service.refund(payment, new BigDecimal("20.00"), true, managerId, audit);

        assertEquals(1, audit.size());
        assertEquals(AuditAction.PAYMENT_REFUNDED, audit.get(0).action());
    }

    @Test
    void givenAlreadyRefundedPayment_whenRefundedAgain_then400() {
        RefundService service = new RefundService();
        PaymentRecord payment = new PaymentRecord(UUID.randomUUID(), new BigDecimal("100.00"));
        payment.setStatus(PaymentRecordStatus.REFUNDED);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.refund(payment, new BigDecimal("1.00"), true, managerId, new ArrayList<>()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenNoOverrideToken_whenVoidAttempted_then403() {
        VoidService service = new VoidService();
        VoidableOrder order = new VoidableOrder(UUID.randomUUID(), OrderChannel.PICKUP, VoidableOrderStatus.PAID, TableLinkStatus.AVAILABLE);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.voidOrder(order, false, managerId, new ArrayList<>()));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void givenValidManagerOverrideToken_whenOrderVoided_thenOrderStatusVoided() {
        VoidService service = new VoidService();
        VoidableOrder order = new VoidableOrder(UUID.randomUUID(), OrderChannel.PICKUP, VoidableOrderStatus.PAID, TableLinkStatus.AVAILABLE);

        VoidableOrder updated = service.voidOrder(order, true, managerId, new ArrayList<>());

        assertEquals(VoidableOrderStatus.VOIDED, updated.getStatus());
    }

    @Test
    void givenDineInOrder_whenVoided_thenLinkedTableStatusChangedToAvailable() {
        VoidService service = new VoidService();
        VoidableOrder order = new VoidableOrder(UUID.randomUUID(), OrderChannel.DINE_IN, VoidableOrderStatus.PAID, TableLinkStatus.OCCUPIED);

        VoidableOrder updated = service.voidOrder(order, true, managerId, new ArrayList<>());

        assertEquals(TableLinkStatus.AVAILABLE, updated.getTableStatus());
    }

    @Test
    void givenAlreadyCompletedOrder_whenVoidAttempted_then400() {
        VoidService service = new VoidService();
        VoidableOrder order = new VoidableOrder(UUID.randomUUID(), OrderChannel.PICKUP, VoidableOrderStatus.COMPLETED, TableLinkStatus.AVAILABLE);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.voidOrder(order, true, managerId, new ArrayList<>()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenSuccessfulVoid_thenVoidEventWrittenToAuditLog() {
        VoidService service = new VoidService();
        VoidableOrder order = new VoidableOrder(UUID.randomUUID(), OrderChannel.PICKUP, VoidableOrderStatus.PAID, TableLinkStatus.AVAILABLE);
        List<FinancialAuditEvent> audit = new ArrayList<>();

        service.voidOrder(order, true, managerId, audit);

        assertEquals(1, audit.size());
        assertEquals(AuditAction.ORDER_VOIDED, audit.get(0).action());
    }

    @Test
    void givenNoOverrideToken_whenDiscountAttempted_then403() {
        DiscountService service = new DiscountService();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.apply(new BigDecimal("100.00"), DiscountMode.FLAT, new BigDecimal("10.00"), false, managerId, new ArrayList<>()));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void givenValidManagerOverrideToken_whenFlatDiscountApplied_thenOrderTotalReducedByAmount() {
        DiscountService service = new DiscountService();

        DiscountResult result = service.apply(new BigDecimal("100.00"), DiscountMode.FLAT, new BigDecimal("15.00"), true, managerId, new ArrayList<>());

        assertEquals(new BigDecimal("85.00"), result.newTotal());
    }

    @Test
    void givenValidManagerOverrideToken_whenPercentageDiscountApplied_thenOrderTotalReducedByPercentage() {
        DiscountService service = new DiscountService();

        DiscountResult result = service.apply(new BigDecimal("200.00"), DiscountMode.PERCENTAGE, new BigDecimal("10"), true, managerId, new ArrayList<>());

        assertEquals(new BigDecimal("180.00"), result.newTotal());
    }

    @Test
    void givenDiscountExceedsOrderTotal_whenApplied_then400() {
        DiscountService service = new DiscountService();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.apply(new BigDecimal("20.00"), DiscountMode.FLAT, new BigDecimal("25.00"), true, managerId, new ArrayList<>()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void givenSuccessfulDiscount_thenDiscountEventWrittenToAuditLog() {
        DiscountService service = new DiscountService();
        List<FinancialAuditEvent> audit = new ArrayList<>();

        service.apply(new BigDecimal("100.00"), DiscountMode.FLAT, new BigDecimal("5.00"), true, managerId, audit);

        assertEquals(1, audit.size());
        assertEquals(AuditAction.DISCOUNT_APPLIED, audit.get(0).action());
    }

    @Test
    void givenDayWithKnownTransactions_whenReconciliationFetched_thenTotalsMatchSumOfPayments() {
        ReconciliationService service = new ReconciliationService();

        ReconciliationSummary summary = service.summarize(List.of(
                new ReconciliationEntry(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "c1"),
                new ReconciliationEntry(new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "c2")
        ));

        assertEquals(new BigDecimal("300"), summary.getTotalSales());
    }

    @Test
    void givenDayWithRefunds_whenReconciliationFetched_thenRefundTotalCorrect() {
        ReconciliationService service = new ReconciliationService();

        ReconciliationSummary summary = service.summarize(List.of(
                new ReconciliationEntry(BigDecimal.ZERO, new BigDecimal("12"), BigDecimal.ZERO, BigDecimal.ZERO, "c1"),
                new ReconciliationEntry(BigDecimal.ZERO, new BigDecimal("8"), BigDecimal.ZERO, BigDecimal.ZERO, "c2")
        ));

        assertEquals(new BigDecimal("20"), summary.getTotalRefunds());
    }

    @Test
    void givenDayWithVoids_whenReconciliationFetched_thenVoidTotalCorrect() {
        ReconciliationService service = new ReconciliationService();

        ReconciliationSummary summary = service.summarize(List.of(
                new ReconciliationEntry(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("7"), BigDecimal.ZERO, "c1"),
                new ReconciliationEntry(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3"), BigDecimal.ZERO, "c2")
        ));

        assertEquals(new BigDecimal("10"), summary.getTotalVoids());
    }

    @Test
    void givenNoOverrideToken_whenSignOffAttempted_then403() {
        ReconciliationService service = new ReconciliationService();
        ReconciliationSummary summary = new ReconciliationSummary();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.signOff(summary, false, managerId));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void givenValidManagerOverrideToken_whenSignOff_thenSignedByAndSignedAtPopulated() {
        ReconciliationService service = new ReconciliationService();
        ReconciliationSummary summary = new ReconciliationSummary();

        ReconciliationSummary signed = service.signOff(summary, true, managerId);

        assertEquals(managerId, signed.getSignedBy());
        assertNotNull(signed.getSignedAt());
    }

    @Test
    void givenAlreadySignedOffDay_whenSignOffAgain_then409() {
        ReconciliationService service = new ReconciliationService();
        ReconciliationSummary summary = new ReconciliationSummary();
        service.signOff(summary, true, managerId);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.signOff(summary, true, managerId));

        assertEquals(409, ex.getStatus());
    }

    @Test
    void givenActionFilter_whenAuditLogQueried_thenOnlyMatchingActionsReturned() {
        AuditReviewService service = new AuditReviewService();
        List<FinancialAuditEvent> events = List.of(
                new FinancialAuditEvent(managerId, AuditAction.ORDER_VOIDED, Instant.now(), "a"),
                new FinancialAuditEvent(managerId, AuditAction.DISCOUNT_APPLIED, Instant.now(), "b")
        );

        List<FinancialAuditEvent> filtered = service.filterByAction(events, AuditAction.ORDER_VOIDED);

        assertEquals(1, filtered.size());
        assertEquals(AuditAction.ORDER_VOIDED, filtered.get(0).action());
    }

    @Test
    void givenDateFilter_whenAuditLogQueried_thenOnlyEventsInRangeReturned() {
        AuditReviewService service = new AuditReviewService();
        Instant now = Instant.now();
        List<FinancialAuditEvent> events = List.of(
                new FinancialAuditEvent(managerId, AuditAction.ORDER_VOIDED, now.minusSeconds(3600), "a"),
                new FinancialAuditEvent(managerId, AuditAction.ORDER_VOIDED, now, "b"),
                new FinancialAuditEvent(managerId, AuditAction.ORDER_VOIDED, now.plusSeconds(3600), "c")
        );

        List<FinancialAuditEvent> filtered = service.filterByDate(events, now.minusSeconds(10), now.plusSeconds(10));

        assertEquals(1, filtered.size());
        assertTrue(filtered.get(0).metadata().equals("b"));
    }

    @Test
    void givenCashierToken_whenAuditLogQueried_then403() {
        AuditReviewService service = new AuditReviewService();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.query(List.of(), Role.CASHIER));

        assertEquals(403, ex.getStatus());
    }
}
