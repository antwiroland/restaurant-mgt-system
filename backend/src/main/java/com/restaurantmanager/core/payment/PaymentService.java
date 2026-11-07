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
    private final com.restaurantmanager.core.billing.BillingService billingService;
    private final com.restaurantmanager.core.payment.split.SplitService splitService;

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
                          TableSessionService tableSessionService,
                          com.restaurantmanager.core.billing.BillingService billingService,
                          com.restaurantmanager.core.payment.split.SplitService splitService) {
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
        this.billingService = billingService;
        this.splitService = splitService;
    }

    @Transactional
    public PaymentInitiateResponse initiate(PaymentInitiateRequest request, UserPrincipal principal) {
        ensureProviderConfigured();
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
        }

        return start(order, amount, splitId, request.method(), request.momoPhone().trim(), key);
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
                key, existing.getAmount(), existing.getCurrency(), request.momoPhone().trim()
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
        retry.setMomoPhone(request.momoPhone().trim());
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
                payment.getProviderMessage() == null ? "Approve payment on your phone" : payment.getProviderMessage()
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
}
