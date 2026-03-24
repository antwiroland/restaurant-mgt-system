package com.restaurantmanager.core.phase9.runtime;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.common.TokenType;
import com.restaurantmanager.core.phase9.audit.AuditReviewService;
import com.restaurantmanager.core.phase9.audit.FinancialAuditEvent;
import com.restaurantmanager.core.phase9.discount.DiscountMode;
import com.restaurantmanager.core.phase9.discount.DiscountResult;
import com.restaurantmanager.core.phase9.discount.DiscountService;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationEntry;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationService;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationSummary;
import com.restaurantmanager.core.phase9.refund.PaymentRecord;
import com.restaurantmanager.core.phase9.refund.RefundService;
import com.restaurantmanager.core.phase9.voiding.OrderChannel;
import com.restaurantmanager.core.phase9.voiding.TableLinkStatus;
import com.restaurantmanager.core.phase9.voiding.VoidService;
import com.restaurantmanager.core.phase9.voiding.VoidableOrder;
import com.restaurantmanager.core.phase9.voiding.VoidableOrderStatus;
import com.restaurantmanager.core.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Phase9RuntimeService {
    private final DiscountService discountService = new DiscountService();
    private final RefundService refundService = new RefundService();
    private final VoidService voidService = new VoidService();
    private final ReconciliationService reconciliationService = new ReconciliationService();
    private final AuditReviewService auditReviewService = new AuditReviewService();

    private final List<FinancialAuditEvent> auditEvents = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<LocalDate, ReconciliationSummary> reconciliationByDate = new ConcurrentHashMap<>();

    private final JwtService jwtService;

    public Phase9RuntimeService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public DiscountResult applyDiscount(BigDecimal total,
                                        DiscountMode mode,
                                        BigDecimal value,
                                        String overrideToken,
                                        UUID actorId) {
        validateOverrideToken(overrideToken, OverrideActionType.DISCOUNT);
        return discountService.apply(total, mode, value, true, actorId, auditEvents);
    }

    public PaymentRecord refund(UUID paymentId,
                                BigDecimal paidAmount,
                                BigDecimal amount,
                                String overrideToken,
                                UUID actorId) {
        validateOverrideToken(overrideToken, OverrideActionType.REFUND);
        PaymentRecord paymentRecord = new PaymentRecord(paymentId, paidAmount);
        return refundService.refund(paymentRecord, amount, true, actorId, auditEvents);
    }

    public VoidableOrder voidOrder(UUID orderId,
                                   OrderChannel channel,
                                   VoidableOrderStatus status,
                                   TableLinkStatus tableStatus,
                                   String overrideToken,
                                   UUID actorId) {
        validateOverrideToken(overrideToken, OverrideActionType.VOID);
        VoidableOrder order = new VoidableOrder(orderId, channel, status, tableStatus);
        return voidService.voidOrder(order, true, actorId, auditEvents);
    }

    public ReconciliationSummary summarize(LocalDate businessDate, List<ReconciliationEntry> entries) {
        ReconciliationSummary summary = reconciliationService.summarize(entries);
        reconciliationByDate.put(businessDate, summary);
        return summary;
    }

    public ReconciliationSummary signOff(LocalDate businessDate, String overrideToken, UUID managerId) {
        validateAnyOverrideToken(overrideToken);
        ReconciliationSummary summary = reconciliationByDate.get(businessDate);
        if (summary == null) {
            throw new ApiException(404, "No reconciliation summary for date");
        }
        return reconciliationService.signOff(summary, true, managerId);
    }

    public List<FinancialAuditEvent> queryAudit(Role requesterRole, AuditAction action, Instant from, Instant to) {
        List<FinancialAuditEvent> base = auditReviewService.query(auditEvents, requesterRole);
        if (action != null) {
            base = auditReviewService.filterByAction(base, action);
        }
        if (from != null && to != null) {
            base = auditReviewService.filterByDate(base, from, to);
        }
        return base;
    }

    private void validateOverrideToken(String overrideToken, OverrideActionType expectedAction) {
        Claims claims = parseOverride(overrideToken);
        OverrideActionType actionType = jwtService.actionType(claims);
        if (actionType != expectedAction) {
            throw new ApiException(403, "Override token not valid for this action");
        }
    }

    private void validateAnyOverrideToken(String overrideToken) {
        parseOverride(overrideToken);
    }

    private Claims parseOverride(String overrideToken) {
        if (overrideToken == null || overrideToken.isBlank()) {
            throw new ApiException(403, "Override token required");
        }
        try {
            Claims claims = jwtService.parse(overrideToken);
            if (jwtService.tokenType(claims) != TokenType.OVERRIDE) {
                throw new ApiException(401, "Invalid override token");
            }
            return claims;
        } catch (ExpiredJwtException ex) {
            throw new ApiException(401, "Override token expired");
        }
    }
}
