package com.restaurantmanager.core.financial;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.audit.AuditLogEntity;
import com.restaurantmanager.core.audit.AuditLogRepository;
import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.common.TokenType;
import com.restaurantmanager.core.financial.dto.FinancialDtos.DiscountRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.DiscountResponse;
import com.restaurantmanager.core.financial.dto.FinancialDtos.FinancialAuditRecord;
import com.restaurantmanager.core.financial.dto.FinancialDtos.MethodTotal;
import com.restaurantmanager.core.financial.dto.FinancialDtos.ReconciliationReport;
import com.restaurantmanager.core.financial.dto.FinancialDtos.RefundRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.RefundResponse;
import com.restaurantmanager.core.financial.dto.FinancialDtos.ShiftCashSummary;
import com.restaurantmanager.core.financial.dto.FinancialDtos.SignOff;
import com.restaurantmanager.core.financial.dto.FinancialDtos.SignOffRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.VoidRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.VoidResponse;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderPricingService;
import com.restaurantmanager.core.order.OrderRealtimePublisher;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentRefundEntity;
import com.restaurantmanager.core.payment.PaymentRefundRepository;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentService;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.promotion.DiscountType;
import com.restaurantmanager.core.security.JwtService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.shift.CashierShiftEntity;
import com.restaurantmanager.core.shift.CashierShiftRepository;
import com.restaurantmanager.core.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Manager-approved financial actions (discount, void, refund) applied to persisted orders and payments,
 * daily reconciliation computed from the ledger, and the financial audit trail (stored in audit_logs).
 */
@Service
public class FinancialService {
    private static final Set<AuditAction> FINANCIAL_ACTIONS = Set.of(
            AuditAction.DISCOUNT_APPLIED, AuditAction.ORDER_VOIDED, AuditAction.PAYMENT_REFUNDED, AuditAction.RECONCILIATION_SIGNED_OFF);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JwtService jwtService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentService paymentService;
    private final OrderPricingService pricingService;
    private final OrderRealtimePublisher orderRealtimePublisher;
    private final CashierShiftRepository shiftRepository;
    private final ReconciliationSignOffRepository signOffRepository;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.restaurantmanager.core.inventory.InventoryService inventoryService;

    public FinancialService(JwtService jwtService,
                            OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            PaymentRefundRepository refundRepository,
                            PaymentService paymentService,
                            OrderPricingService pricingService,
                            OrderRealtimePublisher orderRealtimePublisher,
                            CashierShiftRepository shiftRepository,
                            ReconciliationSignOffRepository signOffRepository,
                            AuditService auditService,
                            AuditLogRepository auditLogRepository,
                            UserRepository userRepository,
                            ObjectMapper objectMapper,
                            com.restaurantmanager.core.inventory.InventoryService inventoryService) {
        this.jwtService = jwtService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentService = paymentService;
        this.pricingService = pricingService;
        this.orderRealtimePublisher = orderRealtimePublisher;
        this.shiftRepository = shiftRepository;
        this.signOffRepository = signOffRepository;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
    }

    // ---------------------------------------------------------------- discount

    @Transactional
    public DiscountResponse applyDiscount(DiscountRequest request, UserPrincipal principal, String ipAddress) {
        UUID approverId = requireOverride(request.overrideToken(), OverrideActionType.DISCOUNT);
        if (request.orderId() == null || request.mode() == null || request.value() == null) {
            throw new ApiException(400, "orderId, mode and value are required");
        }
        if (request.value().signum() <= 0) {
            throw new ApiException(400, "Discount value must be positive");
        }
        OrderEntity order = loadOrder(request.orderId(), principal);
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new ApiException(409, "Discount cannot be applied to a " + order.getStatus().name().toLowerCase() + " order");
        }
        boolean hasPayment = !paymentRepository.findByOrderIdAndStatusIn(order.getId(),
                List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING, PaymentStatus.SUCCESS)).isEmpty();
        if (hasPayment) {
            throw new ApiException(409, "Order already has a payment in progress or completed; refund instead");
        }

        // The manual discount applies to what is payable after automatic discounts; it replaces any earlier manual discount.
        BigDecimal payable = order.getSubtotal().add(order.getDeliveryFee())
                .subtract(order.getPromoDiscount()).subtract(order.getOfferDiscount()).subtract(order.getLoyaltyDiscount());
        BigDecimal discount = request.mode() == DiscountType.FLAT
                ? request.value()
                : payable.multiply(request.value()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        if (request.mode() == DiscountType.PERCENTAGE && request.value().compareTo(HUNDRED) > 0) {
            throw new ApiException(400, "Percentage discount cannot exceed 100");
        }
        if (discount.compareTo(payable) > 0) {
            throw new ApiException(400, "Discount exceeds order total");
        }

        BigDecimal previousTotal = order.getTotal();
        order.setManualDiscount(discount);
        order.setManualDiscountReason(blankToNull(request.reason()));
        pricingService.reprice(order);
        orderRepository.save(order);

        audit(approverId, AuditAction.DISCOUNT_APPLIED, "ORDER", order.getId(), ipAddress, mapOf(
                "requestedBy", principal.userId(),
                "mode", request.mode(),
                "value", request.value(),
                "discount", discount,
                "previousTotal", previousTotal,
                "newTotal", order.getTotal(),
                "reason", blankToNull(request.reason())));
        return new DiscountResponse("OK", OverrideActionType.DISCOUNT, approverId, order.getId(), discount,
                order.getSubtotal(), order.getTotal());
    }

    // ---------------------------------------------------------------- void

    @Transactional
    public VoidResponse voidOrder(VoidRequest request, UserPrincipal principal, String ipAddress) {
        UUID approverId = requireOverride(request.overrideToken(), OverrideActionType.VOID);
        if (request.orderId() == null) {
            throw new ApiException(400, "orderId is required");
        }
        OrderEntity order = loadOrder(request.orderId(), principal);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(409, "Order is already cancelled or voided");
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new ApiException(400, "Completed orders cannot be voided; refund the payment instead");
        }
        if (!paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS).isEmpty()) {
            throw new ApiException(409, "Order has a successful payment; refund it instead of voiding");
        }

        int paymentsVoided = paymentService.voidOpenPayments(order.getId());
        pricingService.releaseDiscounts(order);

        OrderStatus previous = order.getStatus();
        inventoryService.restoreForOrder(order, previous);
        Instant now = Instant.now();
        String reason = blankToNull(request.reason());
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason == null ? "Voided" : "Voided: " + reason);
        order.setVoidedAt(now);
        order.setVoidedBy(approverId);
        orderRepository.save(order);
        orderRealtimePublisher.publishOrderStatusChanged(order.getId(), previous, OrderStatus.CANCELLED,
                order.getTable() == null ? null : order.getTable().getQrToken());
        paymentService.refreshTableOccupancy(order);

        audit(approverId, AuditAction.ORDER_VOIDED, "ORDER", order.getId(), ipAddress, mapOf(
                "requestedBy", principal.userId(),
                "previousStatus", previous,
                "amount", order.getTotal(),
                "paymentsVoided", paymentsVoided,
                "reason", reason));
        return new VoidResponse("OK", OverrideActionType.VOID, approverId, order.getId(), order.getTotal(), paymentsVoided, now);
    }

    // ---------------------------------------------------------------- refund

    @Transactional
    public RefundResponse refund(RefundRequest request, UserPrincipal principal, String ipAddress) {
        UUID approverId = requireOverride(request.overrideToken(), OverrideActionType.REFUND);
        if (request.paymentId() == null) {
            throw new ApiException(400, "paymentId is required");
        }
        PaymentEntity payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        assertBranchAccess(payment.getOrder(), principal);

        PaymentService.RefundOutcome outcome = paymentService.refund(
                payment.getId(), request.amount(), blankToNull(request.reason()), principal.userId(), approverId);

        audit(approverId, AuditAction.PAYMENT_REFUNDED, "PAYMENT", payment.getId(), ipAddress, mapOf(
                "requestedBy", principal.userId(),
                "orderId", outcome.orderId(),
                "amount", outcome.amount(),
                "totalRefunded", outcome.totalRefunded(),
                "paymentStatus", outcome.paymentStatus(),
                "providerStatus", outcome.providerStatus(),
                "reason", blankToNull(request.reason())));
        return new RefundResponse("OK", OverrideActionType.REFUND, approverId, outcome.refundId(), outcome.paymentId(),
                outcome.orderId(), outcome.amount(), outcome.totalRefunded(), outcome.remainingRefundable(),
                outcome.paymentStatus(), outcome.providerStatus());
    }

    // ---------------------------------------------------------------- reconciliation

    @Transactional(readOnly = true)
    public ReconciliationReport reconciliation(LocalDate businessDate, UUID branchId, UserPrincipal principal) {
        UUID effectiveBranch = effectiveBranch(branchId, principal);
        Instant from = businessDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = businessDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Predicate<OrderEntity> inBranch = order -> effectiveBranch == null
                || (order.getBranch() != null && effectiveBranch.equals(order.getBranch().getId()));

        List<PaymentEntity> paid = paymentRepository.findPaidBetween(
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED), from, to).stream()
                .filter(p -> inBranch.test(p.getOrder()))
                .toList();
        List<PaymentRefundEntity> refunds = refundRepository.findSuccessfulBetween(from, to).stream()
                .filter(r -> inBranch.test(r.getPayment().getOrder()))
                .toList();
        List<OrderEntity> voided = orderRepository.findByVoidedAtGreaterThanEqualAndVoidedAtLessThan(from, to).stream()
                .filter(inBranch)
                .toList();
        List<OrderEntity> dayOrders = orderRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusNot(from, to, OrderStatus.CANCELLED).stream()
                .filter(inBranch)
                .toList();

        BigDecimal gross = sum(paid.stream().map(PaymentEntity::getAmount).toList());
        BigDecimal refundTotal = sum(refunds.stream().map(PaymentRefundEntity::getAmount).toList());
        BigDecimal voidTotal = sum(voided.stream().map(OrderEntity::getTotal).toList());
        BigDecimal discountTotal = sum(dayOrders.stream().map(OrderEntity::totalDiscount).toList());

        Map<PaymentMethod, BigDecimal> amountByMethod = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, Integer> countByMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentEntity payment : paid) {
            amountByMethod.merge(payment.getMethod(), payment.getAmount(), BigDecimal::add);
            countByMethod.merge(payment.getMethod(), 1, Integer::sum);
        }
        BigDecimal cashRefunds = sum(refunds.stream()
                .filter(r -> r.getPayment().getMethod() == PaymentMethod.CASH)
                .map(PaymentRefundEntity::getAmount).toList());
        BigDecimal expectedCash = amountByMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO).subtract(cashRefunds);
        List<MethodTotal> byMethod = amountByMethod.entrySet().stream()
                .map(e -> new MethodTotal(e.getKey(), scale(e.getValue()), countByMethod.get(e.getKey())))
                .toList();

        List<CashierShiftEntity> shifts = shiftRepository.findByClosedAtGreaterThanEqualAndClosedAtLessThan(from, to).stream()
                .filter(s -> effectiveBranch == null || (s.getBranch() != null && effectiveBranch.equals(s.getBranch().getId())))
                .toList();
        ShiftCashSummary shiftSummary = new ShiftCashSummary(
                shifts.size(),
                scale(sum(shifts.stream().map(CashierShiftEntity::getClosingCash).filter(Objects::nonNull).toList())),
                scale(sum(shifts.stream().map(CashierShiftEntity::getExpectedCash).filter(Objects::nonNull).toList())),
                scale(sum(shifts.stream().map(CashierShiftEntity::getVariance).filter(Objects::nonNull).toList())));

        SignOff signOff = signOffRepository.findForDateAndBranch(businessDate, effectiveBranch)
                .map(this::toSignOff)
                .orElse(null);

        long paidOrders = paid.stream().map(p -> p.getOrder().getId()).distinct().count();
        return new ReconciliationReport(businessDate, effectiveBranch, scale(gross), scale(refundTotal), scale(voidTotal),
                scale(discountTotal), scale(gross.subtract(refundTotal)), scale(expectedCash), (int) paidOrders,
                voided.size(), refunds.size(), byMethod, shiftSummary, signOff);
    }

    @Transactional
    public ReconciliationReport signOff(LocalDate businessDate, SignOffRequest request, UserPrincipal principal, String ipAddress) {
        UUID approverId = requireAnyOverride(request.overrideToken());
        UUID branchId = effectiveBranch(request.branchId(), principal);
        if (signOffRepository.findForDateAndBranch(businessDate, branchId).isPresent()) {
            throw new ApiException(409, "Day already signed off");
        }
        if (businessDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ApiException(400, "Cannot sign off a future business date");
        }
        ReconciliationReport report = reconciliation(businessDate, branchId, principal);
        BigDecimal counted = request.countedCash() != null ? request.countedCash()
                : (report.shifts().closedShifts() > 0 ? report.shifts().countedCash() : null);

        ReconciliationSignOffEntity entity = new ReconciliationSignOffEntity();
        entity.setBusinessDate(businessDate);
        entity.setBranchId(branchId);
        entity.setGrossSales(report.grossSales());
        entity.setTotalRefunds(report.totalRefunds());
        entity.setTotalVoids(report.totalVoids());
        entity.setTotalDiscounts(report.totalDiscounts());
        entity.setNetSales(report.netSales());
        entity.setExpectedCash(report.expectedCash());
        entity.setCountedCash(counted == null ? null : scale(counted));
        entity.setCashVariance(counted == null ? null : scale(counted.subtract(report.expectedCash())));
        entity.setNotes(blankToNull(request.notes()));
        entity.setSignedBy(approverId);
        signOffRepository.save(entity);

        audit(approverId, AuditAction.RECONCILIATION_SIGNED_OFF, "RECONCILIATION", entity.getId(), ipAddress, mapOf(
                "requestedBy", principal.userId(),
                "businessDate", businessDate.toString(),
                "branchId", branchId,
                "netSales", report.netSales(),
                "expectedCash", report.expectedCash(),
                "countedCash", entity.getCountedCash(),
                "cashVariance", entity.getCashVariance()));
        return reconciliation(businessDate, branchId, principal);
    }

    @Transactional(readOnly = true)
    public List<SignOff> signOffs(LocalDate from, LocalDate to) {
        return signOffRepository.findByBusinessDateBetweenOrderByBusinessDateDesc(from, to).stream()
                .map(this::toSignOff)
                .toList();
    }

    // ---------------------------------------------------------------- audit

    @Transactional(readOnly = true)
    public List<FinancialAuditRecord> audit(AuditAction action, Instant from, Instant to) {
        if (action != null && !FINANCIAL_ACTIONS.contains(action)) {
            throw new ApiException(400, "Not a financial audit action: " + action);
        }
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.parse("9999-12-31T23:59:59Z") : to;
        List<AuditLogEntity> rows = auditLogRepository.findByActionsBetween(
                action == null ? FINANCIAL_ACTIONS : Set.of(action), start, end);
        return rows.stream()
                .map(row -> new FinancialAuditRecord(row.getId(), row.getAction(),
                        row.getActor() == null ? null : row.getActor().getId(),
                        row.getActor() == null ? null : row.getActor().getName(),
                        row.getEntityType(), row.getEntityId(), row.getMetadata(), row.getCreatedAt()))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private UUID requireOverride(String overrideToken, OverrideActionType expectedAction) {
        Claims claims = parseOverride(overrideToken);
        if (jwtService.actionType(claims) != expectedAction) {
            throw new ApiException(403, "Override token not valid for this action");
        }
        return jwtService.userId(claims);
    }

    private UUID requireAnyOverride(String overrideToken) {
        return jwtService.userId(parseOverride(overrideToken));
    }

    private Claims parseOverride(String overrideToken) {
        if (overrideToken == null || overrideToken.isBlank()) {
            throw new ApiException(403, "Override token required");
        }
        Claims claims;
        try {
            claims = jwtService.parse(overrideToken);
        } catch (ExpiredJwtException ex) {
            throw new ApiException(401, "Override token expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(401, "Invalid override token");
        }
        if (jwtService.tokenType(claims) != TokenType.OVERRIDE) {
            throw new ApiException(401, "Invalid override token");
        }
        Role approverRole = jwtService.role(claims);
        if (approverRole != Role.MANAGER && approverRole != Role.ADMIN) {
            throw new ApiException(403, "Override token must be issued to a manager");
        }
        return claims;
    }

    private OrderEntity loadOrder(UUID orderId, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        assertBranchAccess(order, principal);
        return order;
    }

    private void assertBranchAccess(OrderEntity order, UserPrincipal principal) {
        if (isBranchScoped(principal) && order.getBranch() != null
                && !principal.branchId().equals(order.getBranch().getId())) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private UUID effectiveBranch(UUID requested, UserPrincipal principal) {
        if (isBranchScoped(principal)) {
            if (requested != null && !requested.equals(principal.branchId())) {
                throw new ApiException(403, "Forbidden");
            }
            return principal.branchId();
        }
        return requested;
    }

    private boolean isBranchScoped(UserPrincipal principal) {
        return principal != null
                && (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER)
                && principal.branchId() != null;
    }

    private void audit(UUID actorId, AuditAction action, String entityType, UUID entityId, String ip, Map<String, Object> metadata) {
        auditService.log(userRepository.findById(actorId).orElse(null), action, entityType, entityId, toJson(metadata), ip);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }

    private SignOff toSignOff(ReconciliationSignOffEntity e) {
        return new SignOff(e.getId(), e.getSignedBy(), e.getSignedAt(), e.getCountedCash(), e.getCashVariance(), e.getNotes());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
