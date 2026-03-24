package com.restaurantmanager.core.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.PaymentProps;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.dto.PaymentInitiateRequest;
import com.restaurantmanager.core.payment.dto.PaymentInitiateResponse;
import com.restaurantmanager.core.payment.dto.PaymentResponse;
import com.restaurantmanager.core.payment.dto.PaymentRetryRequest;
import com.restaurantmanager.core.payment.dto.ReceiptResponse;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
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

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository paymentWebhookEventRepository,
                          OrderRepository orderRepository,
                          PaystackClient paystackClient,
                          PaymentProps paymentProps,
                          PaymentRealtimePublisher realtimePublisher,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.orderRepository = orderRepository;
        this.paystackClient = paystackClient;
        this.paymentProps = paymentProps;
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
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
        assertOrderAccess(order, principal);

        PaystackClient.InitiateResult result = paystackClient.initiate(
                key, order.getTotal(), paymentProps.getDefaultCurrency(), request.momoPhone().trim()
        );

        PaymentEntity payment = new PaymentEntity();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setCurrency(paymentProps.getDefaultCurrency());
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setPaystackReference(result.reference());
        payment.setIdempotencyKey(key);
        payment.setAuthorizationUrl(result.authorizationUrl());
        payment.setMomoPhone(request.momoPhone().trim());
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
            OrderEntity order = payment.getOrder();
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
            }
        } else if (mapped == PaymentStatus.FAILED) {
            payment.setFailureReason(providerMessage == null || providerMessage.isBlank() ? "Payment failed" : providerMessage.trim());
        }

        if (previous == PaymentStatus.SUCCESS && mapped != PaymentStatus.SUCCESS) {
            payment.setPaidAt(null);
        }
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
            if (customerId == null || !customerId.equals(principal.userId())) {
                throw new ApiException(403, "Forbidden");
            }
        }
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
                payment.getPaidAt()
        );
    }

    public Map<String, String> webhookOkResponse() {
        return Map.of("status", "ok");
    }
}
