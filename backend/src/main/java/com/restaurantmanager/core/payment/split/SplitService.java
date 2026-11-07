package com.restaurantmanager.core.payment.split;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.order.GroupSessionParticipantEntity;
import com.restaurantmanager.core.order.GroupSessionParticipantRepository;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits an order's total into shares that participants pay separately (by the items they ordered, or equally).
 */
@Service
public class SplitService {
    public enum Mode { BY_ITEMS, EQUAL }

    private final PaymentSplitRepository splitRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final GroupSessionParticipantRepository participantRepository;
    private final PaymentRepository paymentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SplitService(PaymentSplitRepository splitRepository,
                        OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        GroupSessionParticipantRepository participantRepository,
                        PaymentRepository paymentRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.splitRepository = splitRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.participantRepository = participantRepository;
        this.paymentRepository = paymentRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public List<SplitView> createSplits(UUID orderId, SplitRequest request, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        assertCanManage(order, principal);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(409, "Order is cancelled");
        }
        boolean paymentStarted = !paymentRepository.findByOrderIdAndStatusIn(orderId,
                List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING, PaymentStatus.SUCCESS)).isEmpty();
        if (paymentStarted) {
            throw new ApiException(409, "Payment has already started for this order; splits can no longer change");
        }
        splitRepository.deleteAll(splitRepository.findByOrderIdOrderByPositionAsc(orderId));

        List<Share> shares = request.mode() == Mode.BY_ITEMS ? byItems(order) : equal(order, request);
        List<BigDecimal> amounts = SplitAllocator.allocate(order.getTotal(), shares.stream().map(Share::weight).toList());
        List<PaymentSplitEntity> rows = new ArrayList<>();
        for (int i = 0; i < shares.size(); i++) {
            Share share = shares.get(i);
            PaymentSplitEntity split = new PaymentSplitEntity();
            split.setOrderId(orderId);
            split.setParticipantId(share.participantId());
            split.setUserId(share.userId());
            split.setDisplayName(share.name());
            split.setAmount(amounts.get(i));
            split.setPosition(i);
            rows.add(split);
        }
        List<SplitView> views = splitRepository.saveAll(rows).stream().map(SplitView::from).toList();
        publish(orderId);
        return views;
    }

    @Transactional(readOnly = true)
    public List<SplitView> list(UUID orderId, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        List<PaymentSplitEntity> splits = splitRepository.findByOrderIdOrderByPositionAsc(orderId);
        if (principal.role() == Role.CUSTOMER) {
            boolean owner = principal.userId().equals(order.getCustomerUserId());
            boolean participant = splits.stream().anyMatch(s -> principal.userId().equals(s.getUserId()));
            if (!owner && !participant) {
                throw new ApiException(403, "Forbidden");
            }
        }
        return splits.stream().map(SplitView::from).toList();
    }

    /** Active (non-cancelled) splits of an order; empty when the order is paid in one go. */
    @Transactional(readOnly = true)
    public List<PaymentSplitEntity> activeSplits(UUID orderId) {
        return splitRepository.findByOrderIdAndStatusNotOrderByPositionAsc(orderId, PaymentSplitEntity.Status.CANCELLED);
    }

    @Transactional
    public void markPaid(UUID splitId, UUID paymentId) {
        splitRepository.findById(splitId).ifPresent(split -> {
            split.setStatus(PaymentSplitEntity.Status.PAID);
            split.setPaymentId(paymentId);
            split.setPaidAt(Instant.now());
            splitRepository.save(split);
            publish(split.getOrderId());
        });
    }

    @Transactional(readOnly = true)
    public PaymentSplitEntity require(UUID splitId) {
        return splitRepository.findById(splitId).orElseThrow(() -> new ApiException(404, "Split not found"));
    }

    private List<Share> byItems(OrderEntity order) {
        if (order.getGroupSession() == null) {
            throw new ApiException(400, "Splitting by items is only available for group orders");
        }
        List<GroupSessionParticipantEntity> participants = participantRepository.findBySessionId(order.getGroupSession().getId());
        Map<UUID, BigDecimal> itemTotals = new HashMap<>();
        for (OrderItemEntity item : orderItemRepository.findByOrderIdIn(List.of(order.getId()))) {
            UUID key = item.getParticipantId();
            itemTotals.merge(key, item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
        }
        Map<UUID, Share> byParticipant = new LinkedHashMap<>();
        for (GroupSessionParticipantEntity participant : participants) {
            BigDecimal weight = itemTotals.getOrDefault(participant.getId(), BigDecimal.ZERO);
            if (weight.signum() > 0) {
                byParticipant.put(participant.getId(), new Share(participant.getId(), participant.getUserId(), participant.getDisplayName(), weight));
            }
        }
        BigDecimal unassigned = itemTotals.getOrDefault(null, BigDecimal.ZERO);
        if (unassigned.signum() > 0) {
            // items without a participant are charged to the host
            GroupSessionParticipantEntity host = participants.stream()
                    .filter(p -> p.getUserId() != null && p.getUserId().equals(order.getGroupSession().getHostUserId()))
                    .findFirst().orElse(participants.isEmpty() ? null : participants.get(0));
            if (host != null) {
                byParticipant.merge(host.getId(), new Share(host.getId(), host.getUserId(), host.getDisplayName(), unassigned),
                        (a, b) -> new Share(a.participantId(), a.userId(), a.name(), a.weight().add(b.weight())));
            }
        }
        if (byParticipant.isEmpty()) {
            throw new ApiException(400, "No participant items to split");
        }
        return List.copyOf(byParticipant.values());
    }

    private List<Share> equal(OrderEntity order, SplitRequest request) {
        List<Share> shares = new ArrayList<>();
        if (order.getGroupSession() != null && (request.names() == null || request.names().isEmpty()) && request.parts() == null) {
            for (GroupSessionParticipantEntity participant : participantRepository.findBySessionId(order.getGroupSession().getId())) {
                shares.add(new Share(participant.getId(), participant.getUserId(), participant.getDisplayName(), BigDecimal.ONE));
            }
        } else if (request.names() != null && !request.names().isEmpty()) {
            for (String name : request.names()) {
                shares.add(new Share(null, null, name == null || name.isBlank() ? "Guest " + (shares.size() + 1) : name.trim(), BigDecimal.ONE));
            }
        } else {
            int parts = request.parts() == null ? 2 : request.parts();
            for (int i = 1; i <= parts; i++) {
                shares.add(new Share(null, null, "Guest " + i, BigDecimal.ONE));
            }
        }
        if (shares.size() < 2 || shares.size() > 20) {
            throw new ApiException(400, "A bill can be split between 2 and 20 people");
        }
        return shares;
    }

    private void assertCanManage(OrderEntity order, UserPrincipal principal) {
        if (principal.role() == Role.CUSTOMER && !principal.userId().equals(order.getCustomerUserId())) {
            throw new ApiException(403, "Only the person who placed the order can split it");
        }
        if (principal.role() == Role.RIDER) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private void publish(UUID orderId) {
        messagingTemplate.convertAndSend("/topic/orders." + orderId + ".payments",
                Map.of("event", "SPLITS_UPDATED", "orderId", orderId, "updatedAt", Instant.now().toString()));
    }

    record Share(UUID participantId, UUID userId, String name, BigDecimal weight) {}

    public record SplitRequest(@jakarta.validation.constraints.NotNull Mode mode,
                               @jakarta.validation.constraints.Min(2) @jakarta.validation.constraints.Max(20) Integer parts,
                               List<String> names) {}

    public record SplitView(UUID id, UUID orderId, UUID participantId, UUID userId, String displayName, BigDecimal amount,
                            PaymentSplitEntity.Status status, UUID paymentId, Instant paidAt) {
        static SplitView from(PaymentSplitEntity e) {
            return new SplitView(e.getId(), e.getOrderId(), e.getParticipantId(), e.getUserId(), e.getDisplayName(),
                    e.getAmount(), e.getStatus(), e.getPaymentId(), e.getPaidAt());
        }
    }
}
