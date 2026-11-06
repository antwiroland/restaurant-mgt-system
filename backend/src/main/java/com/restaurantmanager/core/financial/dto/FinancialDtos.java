package com.restaurantmanager.core.financial.dto;

import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.promotion.DiscountType;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request/response contracts for manager-approved financial actions.
 * Target fields are validated after the override token so that token errors (401/403) take precedence.
 */
public final class FinancialDtos {
    private FinancialDtos() {
    }

    public record DiscountRequest(@NotBlank String overrideToken, UUID orderId, DiscountType mode, BigDecimal value, String reason) {}

    public record VoidRequest(@NotBlank String overrideToken, UUID orderId, String reason) {}

    public record RefundRequest(@NotBlank String overrideToken, UUID paymentId, BigDecimal amount, String reason) {}

    public record SignOffRequest(@NotBlank String overrideToken, UUID branchId, BigDecimal countedCash, String notes) {}

    public record DiscountResponse(String status, OverrideActionType actionType, UUID approverUserId, UUID orderId,
                                   BigDecimal discountAmount, BigDecimal subtotal, BigDecimal newTotal) {}

    public record VoidResponse(String status, OverrideActionType actionType, UUID approverUserId, UUID orderId,
                               BigDecimal voidedAmount, int paymentsVoided, Instant voidedAt) {}

    public record RefundResponse(String status, OverrideActionType actionType, UUID approverUserId, UUID refundId,
                                 UUID paymentId, UUID orderId, BigDecimal amount, BigDecimal totalRefunded,
                                 BigDecimal remainingRefundable, PaymentStatus paymentStatus, String providerStatus) {}

    public record MethodTotal(PaymentMethod method, BigDecimal amount, int count) {}

    public record ShiftCashSummary(int closedShifts, BigDecimal countedCash, BigDecimal expectedCash, BigDecimal variance) {}

    public record ReconciliationReport(
            LocalDate businessDate,
            UUID branchId,
            BigDecimal grossSales,
            BigDecimal totalRefunds,
            BigDecimal totalVoids,
            BigDecimal totalDiscounts,
            BigDecimal netSales,
            BigDecimal expectedCash,
            int paidOrders,
            int voidedOrders,
            int refundCount,
            List<MethodTotal> byMethod,
            ShiftCashSummary shifts,
            SignOff signOff
    ) {}

    public record SignOff(UUID id, UUID signedBy, Instant signedAt, BigDecimal countedCash, BigDecimal cashVariance, String notes) {}

    public record FinancialAuditRecord(UUID id, AuditAction action, UUID actorId, String actorName, String entityType,
                                       UUID entityId, String metadata, Instant createdAt) {}
}
