package com.restaurantmanager.core.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.loyalty.LoyaltyAccountService;
import com.restaurantmanager.core.table.qr.TableSessionService;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.dto.PaymentInitiateRequest;
import com.restaurantmanager.core.payment.dto.PaymentInitiateResponse;
import com.restaurantmanager.core.payment.dto.PaymentResponse;
import com.restaurantmanager.core.payment.dto.PaymentRetryRequest;
import com.restaurantmanager.core.payment.dto.ReceiptResponse;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.table.TableRealtimePublisher;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final OrderRepository orderRepository;
    private final PaystackClient paystackClient;
    private final PaymentProps paymentProps;
    private final PaymentRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;
    private final RestaurantTableRepository tableRepository;
    private final TableRealtimePublisher tableRealtimePublisher;
    private final CacheManager cacheManager;
    private final WhatsAppService whatsAppService;
    private final UserRepository userRepository;
    private final PaymentRefundRepository refundRepository;
    private final LoyaltyAccountService loyaltyAccountService;
    private final TableSessionService tableSessionService;
    private final com.restaurantmanager.core.table.TableSittingService tableSitting;
    private final com.restaurantmanager.core.billing.BillingService billingService;
    private final com.restaurantmanager.core.payment.split.SplitService splitService;
    private final com.restaurantmanager.core.shift.CashDrawer cashDrawer;
    private final com.restaurantmanager.core.notification.push.PushNotificationService pushNotifications;
    private final com.restaurantmanager.core.order.OrderRealtimePublisher orderRealtimePublisher;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository paymentWebhookEventRepository,
                          OrderRepository orderRepository,
                          PaystackClient paystackClient,
                          PaymentProps paymentProps,
                          PaymentRealtimePublisher realtimePublisher,
                          ObjectMapper objectMapper,
                          RestaurantTableRepository tableRepository,
                          TableRealtimePublisher tableRealtimePublisher,
                          CacheManager cacheManager,
                          WhatsAppService whatsAppService,
                          UserRepository userRepository,
                          PaymentRefundRepository refundRepository,
                          LoyaltyAccountService loyaltyAccountService,
                          TableSessionService tableSessionService, com.restaurantmanager.core.table.TableSittingService tableSitting,
                          com.restaurantmanager.core.billing.BillingService billingService,
                          com.restaurantmanager.core.payment.split.SplitService splitService,
            com.restaurantmanager.core.notification.push.PushNotificationService pushNotifications,
                          com.restaurantmanager.core.order.OrderRealtimePublisher orderRealtimePublisher,
                          com.restaurantmanager.core.shift.CashDrawer cashDrawer) {
        this.cashDrawer = cashDrawer;
        this.pushNotifications = pushNotifications;
        this.orderRealtimePublisher = orderRealtimePublisher;
        this.paymentRepository = paymentRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.orderRepository = orderRepository;
        this.paystackClient = paystackClient;
        this.paymentProps = paymentProps;
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
        this.tableRepository = tableRepository;
        this.tableRealtimePublisher = tableRealtimePublisher;
        this.cacheManager = cacheManager;
        this.whatsAppService = whatsAppService;
        this.userRepository = userRepository;
        this.refundRepository = refundRepository;
        this.loyaltyAccountService = loyaltyAccountService;
        this.tableSessionService = tableSessionService;
        this.tableSitting = tableSitting;
        this.billingService = billingService;
        this.splitService = splitService;
    }

    @Transactional
    public PaymentInitiateResponse initiate(PaymentInitiateRequest request, UserPrincipal principal) {
        boolean cash = request.method() == PaymentMethod.CASH;
        if (cash && principal.role() == Role.CUSTOMER) {
            throw new ApiException(400, "Cash is paid to staff at the counter");
        }
        if (request.method() == PaymentMethod.MOBILE_MONEY && (request.momoPhone() == null || request.momoPhone().isBlank())) {
            throw new ApiException(400, "momoPhone is required for MOBILE_MONEY");
        }
        if (!cash) {
            ensureProviderConfigured();
        }
        String key = request.idempotencyKey().trim();
        PaymentEntity existing = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            assertPaymentAccess(existing, principal);
            if (!existing.getOrder().getId().equals(request.orderId())) {
                throw new ApiException(409, "Idempotency key already used for a different order");
            }
            return toInitiateResponse(existing);
        }

        OrderEntity order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        BigDecimal amount = order.getTotal();
        UUID splitId = null;
        settleOrReplaceWaiting(order.getId(), request.splitId());
        var splits = splitService.activeSplits(order.getId());
        if (!splits.isEmpty()) {
            if (request.splitId() == null) {
                throw new ApiException(409, "This bill is split; choose the share to pay");
            }
            var split = splits.stream().filter(s -> s.getId().equals(request.splitId())).findFirst()
                    .orElseThrow(() -> new ApiException(404, "Split not found for this order"));
            if (split.getStatus() == com.restaurantmanager.core.payment.split.PaymentSplitEntity.Status.PAID) {
                throw new ApiException(409, "This share has already been paid");
            }
            if (principal.role() == Role.CUSTOMER
                    && !principal.userId().equals(split.getUserId())
                    && !principal.userId().equals(order.getCustomerUserId())) {
                throw new ApiException(403, "Forbidden");
            }
            amount = split.getAmount();
            splitId = split.getId();
        } else {
            if (request.splitId() != null) {
                throw new ApiException(404, "Split not found for this order");
            }
            assertOrderAccess(order, principal);
            amount = amountStillOwed(order);
        }

        if (cash) {
            return recordCash(order, amount, splitId, key, request.amountTendered(), principal);
        }
        return start(order, amount, splitId, request.method(), trimToNull(request.momoPhone()), key);
    }

    /** What is left to pay on an unsplit order; refuses an order that is already paid in full. */
    private BigDecimal amountStillOwed(OrderEntity order) {
        BigDecimal paid = paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS).stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal owed = order.getTotal().subtract(paid);
        if (owed.signum() <= 0) {
            throw new ApiException(409, "Order #" + shortNumber(order.getId()) + " has already been paid");
        }
        return owed;
    }

    /**
     * Before a new attempt on the same order (or share), each attempt still waiting is checked with the provider.
     * One that went through is recorded, so the order then counts as paid and the new attempt is refused. One that
     * did not is closed as replaced, so a late approval cannot go unnoticed as a second live payment.
     */
    private void settleOrReplaceWaiting(UUID orderId, UUID splitId) {
        List<PaymentEntity> waiting = paymentRepository.findByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING))
                .stream()
                .filter(p -> java.util.Objects.equals(p.getSplitId(), splitId))
                .toList();
        for (PaymentEntity payment : waiting) {
            PaymentStatus previous = payment.getStatus();
            PaymentStatus checked = previous;
            String message = null;
            if (payment.getMethod() != PaymentMethod.CASH) {
                try {
                    PaystackClient.VerifyResult result = paystackClient.verify(payment.getPaystackReference());
                    checked = mapProviderStatus(result.providerStatus());
                    message = result.message();
                } catch (RuntimeException e) {
                    log.warn("Could not check waiting payment {} before a new attempt", payment.getPaystackReference(), e);
                }
            }
            if (checked == PaymentStatus.SUCCESS || checked == PaymentStatus.FAILED) {
                applyStatusChange(payment, checked, message);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Replaced by a new payment attempt");
            }
            paymentRepository.save(payment);
            realtimePublisher.publishPaymentStatusChanged(payment.getId(), orderId, previous, payment.getStatus(),
                    payment.getAmount(), payment.getMethod());
        }
    }

    private static String shortNumber(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Cash taken at the counter: no provider round-trip. The payment is recorded as successful straight away
     * and goes through the same success path as a confirmed provider payment (order confirmation, split,
     * table occupancy, loyalty, realtime events).
     */
    private PaymentInitiateResponse recordCash(OrderEntity order, BigDecimal amount, UUID splitId, String key,
                                               BigDecimal tendered, UserPrincipal principal) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getVoidedAt() != null) {
            throw new ApiException(409, "Order is " + order.getStatus().name().toLowerCase());
        }
        if (tendered != null && tendered.compareTo(amount) < 0) {
            throw new ApiException(400, "Amount tendered is less than the amount due (" + amount + ")");
        }
        PaymentEntity payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setSplitId(splitId);
        payment.setAmount(amount);
        payment.setCurrency(paymentProps.getDefaultCurrency());
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setPaystackReference("CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setIdempotencyKey(key);
        payment.setReceivedBy(principal.userId());
        payment.setShiftId(cashDrawer.shiftForCash(principal.userId()));
        if (tendered != null) {
            payment.setAmountTendered(tendered);
            payment.setChangeDue(tendered.subtract(amount));
        }
        // Mark it paid (and run the success side effects) before the single insert, so the row is never stored
        // half-done as INITIATED.
        applyStatusChange(payment, PaymentStatus.SUCCESS, "Cash received");
        PaymentEntity saved = paymentRepository.saveAndFlush(payment);
        // applyStatusChange checked the table before this payment existed: check again now that it is stored, so a
        // table settled in cash is freed (and its sitting ended once the food is served).
        refreshTableOccupancy(order);
        realtimePublisher.publishPaymentStatusChanged(saved.getId(), order.getId(), PaymentStatus.INITIATED,
                PaymentStatus.SUCCESS, saved.getAmount(), saved.getMethod());
        return toInitiateResponse(saved);
    }

    /**
     * A dine-in guest (no account) pays one share of a split bill by Mobile Money from the table QR page.
     * The order must belong to the table and the bill must already be split.
     */
    @Transactional
    public PaymentInitiateResponse initiateGuestShare(String tableToken, UUID orderId, UUID splitId,
                                                      String momoPhone, String idempotencyKey) {
        ensureProviderConfigured();
        OrderEntity order = splitService.requireTableOrder(tableToken, orderId);
        String key = idempotencyKey.trim();
        PaymentEntity existing = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getOrder().getId().equals(orderId) || !splitId.equals(existing.getSplitId())) {
                throw new ApiException(409, "Idempotency key already used for a different payment");
            }
            return toInitiateResponse(existing);
        }
        var split = splitService.activeSplits(orderId).stream().filter(s -> s.getId().equals(splitId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "Split not found for this order"));
        if (split.getStatus() == com.restaurantmanager.core.payment.split.PaymentSplitEntity.Status.PAID) {
            throw new ApiException(409, "This share has already been paid");
        }
        boolean shareInProgress = !paymentRepository.findByOrderIdAndStatusIn(orderId,
                        List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING)).stream()
                .filter(p -> splitId.equals(p.getSplitId()))
                .toList().isEmpty();
        if (shareInProgress) {
            throw new ApiException(409, "A payment for this share is already in progress");
        }
        return start(order, split.getAmount(), splitId, PaymentMethod.MOBILE_MONEY, momoPhone.trim(), key);
    }

    /**
     * A dine-in guest (no account) pays a whole table order by Mobile Money from the table QR page. Only when the
     * bill is not split, nothing has been paid on it yet and no other payment is waiting for approval.
     */
    @Transactional
    public PaymentInitiateResponse initiateGuestFull(String tableToken, UUID orderId, String momoPhone, String idempotencyKey) {
        ensureProviderConfigured();
        OrderEntity order = splitService.requireTableOrder(tableToken, orderId);
        String key = idempotencyKey.trim();
        PaymentEntity existing = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getOrder().getId().equals(orderId) || existing.getSplitId() != null) {
                throw new ApiException(409, "Idempotency key already used for a different payment");
            }
            return toInitiateResponse(existing);
        }
        if (order.getVoidedAt() != null) {
            throw new ApiException(409, "This order is closed");
        }
        if (!splitService.activeSplits(orderId).isEmpty()) {
            throw new ApiException(409, "This bill is split; pay your share instead");
        }
        if (!paymentRepository.findByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING)).isEmpty()) {
            throw new ApiException(409, "A payment for this order is already waiting for approval");
        }
        if (!paymentRepository.findByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.SUCCESS)).isEmpty()) {
            throw new ApiException(409, "This order has already been paid");
        }
        return start(order, order.getTotal(), null, PaymentMethod.MOBILE_MONEY, momoPhone.trim(), key);
    }

    /** Lets the guest page refresh a share payment's status; the payment must belong to that table's order. */
    @Transactional
    public PaymentResponse verifyGuestPayment(String tableToken, UUID orderId, UUID paymentId) {
        ensureProviderConfigured();
        splitService.requireTableOrder(tableToken, orderId);
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        if (!payment.getOrder().getId().equals(orderId)) {
            throw new ApiException(404, "Payment not found");
        }
        if (payment.getStatus() == PaymentStatus.INITIATED || payment.getStatus() == PaymentStatus.PENDING) {
            PaystackClient.VerifyResult verifyResult = paystackClient.verify(payment.getPaystackReference());
            applyStatusChange(payment, mapProviderStatus(verifyResult.providerStatus()), verifyResult.message());
            payment = paymentRepository.save(payment);
        }
        return toResponse(payment);
    }

    private PaymentInitiateResponse start(OrderEntity order, BigDecimal amount, UUID splitId, PaymentMethod method,
                                          String momoPhone, String key) {
        PaystackClient.InitiateResult result = paystackClient.initiate(
                key, amount, paymentProps.getDefaultCurrency(), momoPhone
        );

        PaymentEntity payment = new PaymentEntity();
        payment.setOrder(order);
        payment.setSplitId(splitId);
        payment.setAmount(amount);
        payment.setCurrency(paymentProps.getDefaultCurrency());
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setPaystackReference(result.reference());
        payment.setIdempotencyKey(key);
        payment.setAuthorizationUrl(result.authorizationUrl());
        payment.setMomoPhone(momoPhone);
        payment.setProviderMessage(result.message());
        PaymentEntity saved = paymentRepository.save(payment);
        return toInitiateResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, UserPrincipal principal) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        assertPaymentAccess(payment, principal);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse verify(UUID paymentId, UserPrincipal principal) {
        ensureProviderConfigured();
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        assertPaymentAccess(payment, principal);

        PaystackClient.VerifyResult verifyResult = paystackClient.verify(payment.getPaystackReference());
        PaymentStatus mapped = mapProviderStatus(verifyResult.providerStatus());
        applyStatusChange(payment, mapped, verifyResult.message());
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentInitiateResponse retry(UUID paymentId, PaymentRetryRequest request, UserPrincipal principal) {
        ensureProviderConfigured();
        PaymentEntity existing = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        assertPaymentAccess(existing, principal);
        if (existing.getStatus() != PaymentStatus.FAILED) {
            throw new ApiException(409, "Only failed payments can be retried");
        }

        String key = request.idempotencyKey().trim();
        if (paymentRepository.findByIdempotencyKey(key).isPresent()) {
            throw new ApiException(409, "Idempotency key already used");
        }

        PaystackClient.InitiateResult result = paystackClient.initiate(
                key, existing.getAmount(), existing.getCurrency(), trimToNull(request.momoPhone())
        );

        PaymentEntity retry = new PaymentEntity();
        retry.setOrder(existing.getOrder());
        retry.setSplitId(existing.getSplitId());
        retry.setAmount(existing.getAmount());
        retry.setCurrency(existing.getCurrency());
        retry.setMethod(existing.getMethod());
        retry.setStatus(PaymentStatus.INITIATED);
        retry.setPaystackReference(result.reference());
        retry.setIdempotencyKey(key);
        retry.setAuthorizationUrl(result.authorizationUrl());
        retry.setMomoPhone(trimToNull(request.momoPhone()));
        retry.setProviderMessage(result.message());
        return toInitiateResponse(paymentRepository.save(retry));
    }

    @Transactional
    public void processWebhook(String signatureHeader, String payload) {
        ensureProviderConfigured();
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ApiException(400, "Missing webhook signature");
        }
        if (!isValidSignature(signatureHeader, payload)) {
            throw new ApiException(400, "Invalid webhook signature");
        }

        JsonNode root = parseJson(payload);
        String providerStatus = root.path("data").path("status").asText("");
        String reference = root.path("data").path("reference").asText("");
        if (reference.isBlank()) {
            return;
        }

        String rawEventId = root.path("data").path("id").asText("");
        String eventName = root.path("event").asText("unknown");
        String dedupeKey = rawEventId.isBlank()
                ? eventName + ":" + reference + ":" + providerStatus
                : rawEventId;

        if (paymentWebhookEventRepository.existsById(dedupeKey)) {
            return;
        }

        PaymentEntity payment = paymentRepository.findByPaystackReference(reference)
                .orElse(null);
        if (payment == null) {
            PaymentWebhookEventEntity event = new PaymentWebhookEventEntity();
            event.setEventKey(dedupeKey);
            event.setProcessedAt(Instant.now());
            paymentWebhookEventRepository.save(event);
            return;
        }

        PaymentStatus mapped = mapProviderStatus(providerStatus);
        String reason = root.path("data").path("gateway_response").asText(null);
        PaymentStatus previous = payment.getStatus();
        applyStatusChange(payment, mapped, reason);
        paymentRepository.save(payment);

        PaymentWebhookEventEntity event = new PaymentWebhookEventEntity();
        event.setEventKey(dedupeKey);
        event.setPaymentId(payment.getId());
        event.setProcessedAt(Instant.now());
        paymentWebhookEventRepository.save(event);

        if (previous != payment.getStatus()) {
            realtimePublisher.publishPaymentStatusChanged(
                    payment.getId(), payment.getOrder().getId(), previous, payment.getStatus(),
                    payment.getAmount(), payment.getMethod()
            );
            if (payment.getStatus() == PaymentStatus.FAILED) {
                notifyPaymentFailed(payment);
                realtimePublisher.publishPaymentFailed(payment.getId(), payment.getOrder().getId(),
                        payment.getFailureReason() == null ? "Payment failed" : payment.getFailureReason());
            }
        }
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receiptByPaymentId(UUID paymentId, UserPrincipal principal) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        assertPaymentAccess(payment, principal);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(404, "Receipt not found");
        }
        return toReceipt(payment);
    }

    private static final java.util.regex.Pattern RECEIPT_NUMBER = java.util.regex.Pattern.compile("^RCP-\\d{8}-([0-9A-F]{8})$");
    private static final java.util.regex.Pattern FULL_ID = java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /**
     * Finds a receipt from whatever the cashier has: the order number on the ticket (7D63D7F5), the receipt number
     * (RCP-20261112-2FE7D111), the payment reference (CASH-DADB89D6) or a full order or payment id.
     */
    @Transactional(readOnly = true)
    public ReceiptResponse findReceipt(String query, UserPrincipal principal) {
        String q = query == null ? "" : query.trim().replaceFirst("^#", "");
        String upper = q.toUpperCase(java.util.Locale.ROOT);
        String lower = q.toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) {
            throw new ApiException(400, "Enter an order number, receipt number or payment reference");
        }
        java.util.regex.Matcher receipt = RECEIPT_NUMBER.matcher(upper);
        if (receipt.matches()) {
            PaymentEntity payment = paymentRepository.findByIdPrefix(receipt.group(1).toLowerCase(java.util.Locale.ROOT) + "%",
                            org.springframework.data.domain.PageRequest.of(0, 5)).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst()
                    .orElseThrow(() -> new ApiException(404, "No receipt " + upper));
            return receiptByPaymentId(payment.getId(), principal);
        }
        if (FULL_ID.matcher(lower).matches()) {
            UUID id = UUID.fromString(lower);
            return paymentRepository.existsById(id) ? receiptByPaymentId(id, principal) : receiptByOrderId(id, principal);
        }
        if (lower.matches("[0-9a-f]{6,8}")) {
            List<OrderEntity> orders = orderRepository.findByIdPrefix(lower + "%", org.springframework.data.domain.PageRequest.of(0, 2));
            if (orders.isEmpty()) {
                throw new ApiException(404, "No order with number " + upper);
            }
            if (orders.size() > 1) {
                throw new ApiException(409, "More than one order starts with " + upper + "; enter all 8 characters");
            }
            return receiptByOrderId(orders.get(0).getId(), principal);
        }
        PaymentEntity byReference = paymentRepository.findByPaystackReference(q)
                .or(() -> paymentRepository.findByPaystackReference(upper))
                .orElseThrow(() -> new ApiException(404, "Nothing found for " + q));
        return receiptByPaymentId(byReference.getId(), principal);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receiptByOrderId(UUID orderId, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        assertOrderAccess(order, principal);
        PaymentEntity payment = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.SUCCESS)
                .orElseThrow(() -> new ApiException(404, "Receipt not found"));
        return toReceipt(payment);
    }

    private void applyStatusChange(PaymentEntity payment, PaymentStatus mapped, String providerMessage) {
        PaymentStatus previous = payment.getStatus();
        payment.setStatus(mapped);
        if (providerMessage != null && !providerMessage.isBlank()) {
            payment.setProviderMessage(providerMessage.trim());
        }

        if (mapped == PaymentStatus.SUCCESS && payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
            if (payment.getSplitId() != null) {
                splitService.markPaid(payment.getSplitId(), payment.getId());
            }
            OrderEntity order = payment.getOrder();
            if (order.getStatus() == OrderStatus.PENDING && paidTotal(order, payment).compareTo(order.getTotal()) >= 0) {
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
                pushNotifications.orderStatusChanged(order, OrderStatus.CONFIRMED);
                // A paid pickup order now goes to the kitchen: refresh the KDS and pickup board.
                orderRealtimePublisher.publishOrderStatusChanged(order.getId(), OrderStatus.PENDING, OrderStatus.CONFIRMED,
                        order.getTable() == null ? null : order.getTable().getQrToken());
            }
            refreshTableOccupancy(order);
            awardLoyaltyIfFullyPaid(order, payment);
        } else if (mapped == PaymentStatus.FAILED) {
            payment.setFailureReason(providerMessage == null || providerMessage.isBlank() ? "Payment failed" : providerMessage.trim());
        }

        if (previous == PaymentStatus.SUCCESS && mapped != PaymentStatus.SUCCESS) {
            payment.setPaidAt(null);
        }
    }

    /**
     * Refunds a successful payment (fully or partially) through the payment provider and records the refund.
     * Loyalty points awarded for the order are clawed back proportionally.
     */
    @Transactional
    public RefundOutcome refund(UUID paymentId, BigDecimal requestedAmount, String reason, UUID requestedBy, UUID approvedBy) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(404, "Payment not found"));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ApiException(400, "Payment already fully refunded");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(409, "Only successful payments can be refunded");
        }
        BigDecimal refundable = payment.refundableAmount();
        BigDecimal amount = requestedAmount == null ? refundable : requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ApiException(400, "Refund amount must be positive");
        }
        if (amount.compareTo(refundable) > 0) {
            throw new ApiException(400, "Refund amount exceeds refundable balance of " + refundable);
        }

        // Cash handed back comes out of the drawer of whoever hands it over.
        UUID refundShiftId = payment.getMethod() == PaymentMethod.CASH && requestedBy != null
                ? cashDrawer.shiftForCash(requestedBy)
                : null;
        PaystackClient.RefundResult result = payment.getMethod() == PaymentMethod.CASH
                ? new PaystackClient.RefundResult(null, "processed", "Cash refund recorded")
                : paystackClient.refund(payment.getPaystackReference(), amount, payment.getCurrency());
        if (result.failed()) {
            throw new ApiException(502, "Payment provider rejected refund: " + result.message());
        }

        PaymentRefundEntity refund = new PaymentRefundEntity();
        refund.setPayment(payment);
        refund.setAmount(amount);
        refund.setProviderReference(result.providerReference());
        refund.setProviderStatus(result.providerStatus());
        refund.setReason(reason);
        refund.setRequestedBy(requestedBy);
        refund.setApprovedBy(approvedBy);
        refund.setShiftId(refundShiftId);
        refundRepository.save(refund);

        PaymentStatus previous = payment.getStatus();
        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        payment.setRefundedAt(Instant.now());
        if (payment.refundableAmount().signum() == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        paymentRepository.save(payment);

        clawBackLoyalty(payment.getOrder(), amount);

        if (previous != payment.getStatus()) {
            realtimePublisher.publishPaymentStatusChanged(payment.getId(), payment.getOrder().getId(), previous,
                    payment.getStatus(), payment.getAmount(), payment.getMethod());
        }
        return new RefundOutcome(refund.getId(), payment.getId(), payment.getOrder().getId(), amount,
                payment.getRefundedAmount(), payment.refundableAmount(), payment.getStatus(), result.providerStatus());
    }

    /** Marks open (initiated/pending) payments of an order as voided. */
    @Transactional
    public int voidOpenPayments(UUID orderId) {
        List<PaymentEntity> open = paymentRepository.findByOrderIdAndStatusIn(orderId,
                List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING));
        for (PaymentEntity payment : open) {
            PaymentStatus previous = payment.getStatus();
            payment.setStatus(PaymentStatus.VOIDED);
            paymentRepository.save(payment);
            realtimePublisher.publishPaymentStatusChanged(payment.getId(), orderId, previous, PaymentStatus.VOIDED,
                    payment.getAmount(), payment.getMethod());
        }
        return open.size();
    }

    private void awardLoyaltyIfFullyPaid(OrderEntity order, PaymentEntity confirmedPayment) {
        if (order.getCustomerUserId() == null || order.getLoyaltyPointsAwarded() > 0) {
            return;
        }
        BigDecimal paid = paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS).stream()
                .filter(p -> !p.getId().equals(confirmedPayment.getId()))
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(confirmedPayment.getAmount());
        if (paid.compareTo(order.getTotal()) < 0) {
            return;
        }
        int awarded = loyaltyAccountService.accrueForOrder(order.getCustomerUserId(), order.getTotal(), order.getId().toString());
        if (awarded > 0) {
            order.setLoyaltyPointsAwarded(awarded);
            orderRepository.save(order);
        }
    }

    private void clawBackLoyalty(OrderEntity order, BigDecimal refundedAmount) {
        if (order.getCustomerUserId() == null || order.getLoyaltyPointsAwarded() <= 0 || order.getTotal().signum() <= 0) {
            return;
        }
        int points = BigDecimal.valueOf(order.getLoyaltyPointsAwarded())
                .multiply(refundedAmount)
                .divide(order.getTotal(), 0, RoundingMode.HALF_UP)
                .intValue();
        int deducted = loyaltyAccountService.reverseAccrual(order.getCustomerUserId(), points, order.getId().toString());
        order.setLoyaltyPointsAwarded(Math.max(0, order.getLoyaltyPointsAwarded() - deducted));
        orderRepository.save(order);
    }

    public record RefundOutcome(UUID refundId, UUID paymentId, UUID orderId, BigDecimal amount, BigDecimal totalRefunded,
                                BigDecimal remainingRefundable, PaymentStatus paymentStatus, String providerStatus) {
    }

    public void refreshTableOccupancy(OrderEntity paidOrder) {
        if (paidOrder.getType() != OrderType.DINE_IN || paidOrder.getTable() == null) {
            return;
        }

        RestaurantTableEntity table = tableRepository.findById(paidOrder.getTable().getId())
                .orElse(null);
        if (table == null) {
            return;
        }

        boolean hasOutstanding = orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId()).stream()
                .filter(order -> order.getType() == OrderType.DINE_IN)
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .filter(order -> !order.isWrittenOff())
                .filter(order -> order.getTotal().signum() > 0)
                .anyMatch(order -> {
                    BigDecimal paid = paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS).stream()
                            .map(PaymentEntity::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return paid.compareTo(order.getTotal()) < 0;
                });

        TableStatus newStatus = hasOutstanding ? TableStatus.OCCUPIED : TableStatus.AVAILABLE;
        table.setStatus(newStatus);
        tableRepository.save(table);
        if (newStatus == TableStatus.AVAILABLE) {
            tableSessionService.closeAll(table.getId());
            // Paid in full: once the food is also served, the next party must not see this party's bill.
            tableSitting.endSittingIfFinished(table.getId());
        }
        evictTableCache();
        tableRealtimePublisher.publishTableStatusChanged(table.getNumber(), newStatus);
    }

    private void evictTableCache() {
        var tables = cacheManager.getCache(CacheConfig.TABLES);
        if (tables != null) tables.clear();
        var scan = cacheManager.getCache(CacheConfig.TABLE_SCAN);
        if (scan != null) scan.clear();
    }

    private PaymentStatus mapProviderStatus(String providerStatus) {
        return switch (providerStatus == null ? "" : providerStatus.trim().toLowerCase()) {
            case "success" -> PaymentStatus.SUCCESS;
            case "failed", "abandoned", "error" -> PaymentStatus.FAILED;
            case "pending", "processing" -> PaymentStatus.PENDING;
            default -> PaymentStatus.INITIATED;
        };
    }

    private JsonNode parseJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new ApiException(400, "Invalid webhook payload");
        }
    }

    private boolean isValidSignature(String signatureHeader, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(paymentProps.getPaystack().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return expected.equalsIgnoreCase(signatureHeader.trim());
        } catch (Exception ex) {
            throw new ApiException(400, "Invalid webhook signature");
        }
    }

    private void ensureProviderConfigured() {
        String secret = paymentProps.getPaystack().getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new ApiException(503, "Payment service unavailable");
        }
    }

    private void assertPaymentAccess(PaymentEntity payment, UserPrincipal principal) {
        if (principal.role() == Role.CUSTOMER) {
            UUID customerId = payment.getOrder().getCustomerUserId();
            boolean owner = customerId != null && customerId.equals(principal.userId());
            boolean splitPayer = payment.getSplitId() != null
                    && principal.userId().equals(splitService.require(payment.getSplitId()).getUserId());
            if (!owner && !splitPayer) {
                throw new ApiException(403, "Forbidden");
            }
        }
    }

    /** Sum of successful payments for the order, counting the payment being confirmed even if not yet flushed. */
    private BigDecimal paidTotal(OrderEntity order, PaymentEntity confirmed) {
        return paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS).stream()
                .filter(p -> !p.getId().equals(confirmed.getId()))
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(confirmed.getAmount());
    }

    private void assertOrderAccess(OrderEntity order, UserPrincipal principal) {
        if (principal.role() == Role.CUSTOMER) {
            if (order.getCustomerUserId() == null || !order.getCustomerUserId().equals(principal.userId())) {
                throw new ApiException(403, "Forbidden");
            }
        }
    }

    private PaymentInitiateResponse toInitiateResponse(PaymentEntity payment) {
        return new PaymentInitiateResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getPaystackReference(),
                payment.getAuthorizationUrl(),
                payment.getProviderMessage() == null ? "Approve payment on your phone" : payment.getProviderMessage(),
                payment.getChangeDue()
        );
    }

    private PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getPaystackReference(),
                payment.getAuthorizationUrl(),
                payment.getMomoPhone(),
                payment.getProviderMessage(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }

    private ReceiptResponse toReceipt(PaymentEntity payment) {
        OrderEntity order = payment.getOrder();
        String receiptNumber = "RCP-" + payment.getPaidAt().toString().substring(0, 10).replace("-", "") + "-"
                + payment.getId().toString().substring(0, 8).toUpperCase();
        return new ReceiptResponse(
                receiptNumber,
                payment.getId(),
                order.getId(),
                order.getSubtotal(),
                order.getTotal(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getPaidAt(),
                order.totalDiscount(),
                order.getDeliveryFee(),
                order.getServiceCharge(),
                order.getTaxTotal(),
                order.isTaxInclusive(),
                billingService.taxLines(order.getId()),
                order.getTipAmount(),
                payment.getAmount(),
                payment.getRefundedAmount()
        );
    }

    public Map<String, String> webhookOkResponse() {
        return Map.of("status", "ok");
    }

    @Transactional
    public int reconcilePendingPayments(int olderThanMinutes, int batchSize) {
        ensureProviderConfigured();

        int thresholdMinutes = Math.max(1, olderThanMinutes);
        int maxBatch = Math.max(1, batchSize);
        Instant cutoff = Instant.now().minus(thresholdMinutes, ChronoUnit.MINUTES);

        List<PaymentEntity> candidates = paymentRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(PaymentStatus.PENDING, PaymentStatus.INITIATED), cutoff);
        if (candidates.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (PaymentEntity payment : candidates) {
            if (processed >= maxBatch) {
                break;
            }
            if (payment.getMethod() == PaymentMethod.CASH) {
                // Cash never goes through the provider; one not recorded as received was never taken.
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Cash payment was not completed");
                paymentRepository.save(payment);
                processed++;
                continue;
            }

            PaymentStatus previous = payment.getStatus();
            try {
                PaystackClient.VerifyResult verifyResult = paystackClient.verify(payment.getPaystackReference());
                PaymentStatus mapped = mapProviderStatus(verifyResult.providerStatus());
                applyStatusChange(payment, mapped, verifyResult.message());
                paymentRepository.save(payment);

                if (previous != payment.getStatus()) {
                    realtimePublisher.publishPaymentStatusChanged(
                            payment.getId(), payment.getOrder().getId(), previous, payment.getStatus(),
                            payment.getAmount(), payment.getMethod()
                    );
                    if (payment.getStatus() == PaymentStatus.FAILED) {
                        notifyPaymentFailed(payment);
                        realtimePublisher.publishPaymentFailed(
                                payment.getId(), payment.getOrder().getId(),
                                payment.getFailureReason() == null ? "Payment failed" : payment.getFailureReason()
                        );
                    }
                }
            } catch (Exception ignored) {
                // keep payment pending; next cycle will retry
            }
            processed++;
        }

        return processed;
    }

    private void notifyPaymentFailed(PaymentEntity payment) {
        resolvePaymentPhone(payment).ifPresent(phone ->
                whatsAppService.sendStatusUpdate(phone, "Payment failed. Please retry."));
    }

    private Optional<String> resolvePaymentPhone(PaymentEntity payment) {
        if (payment.getMomoPhone() != null && !payment.getMomoPhone().isBlank()) {
            return Optional.of(payment.getMomoPhone().trim());
        }
        UUID customerId = payment.getOrder().getCustomerUserId();
        if (customerId == null) {
            return Optional.empty();
        }
        return userRepository.findById(customerId)
                .map(UserEntity::getPhone)
                .filter(phone -> phone != null && !phone.isBlank());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
