package com.restaurantmanager.core.kds;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemModifierEntity;
import com.restaurantmanager.core.order.OrderItemModifierRepository;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KdsService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemModifierRepository orderItemModifierRepository;

    public KdsService(OrderRepository orderRepository,
                      OrderItemRepository orderItemRepository,
                      OrderItemModifierRepository orderItemModifierRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemModifierRepository = orderItemModifierRepository;
    }

    @Transactional(readOnly = true)
    public KdsBoardResponse board(UUID branchId, UserPrincipal principal) {
        UUID effectiveBranchId = resolveEffectiveBranch(branchId, principal);
        Map<OrderStatus, List<KdsOrderCard>> columns = new EnumMap<>(OrderStatus.class);
        columns.put(OrderStatus.PENDING, List.of());
        columns.put(OrderStatus.CONFIRMED, List.of());
        columns.put(OrderStatus.PREPARING, List.of());
        columns.put(OrderStatus.READY, List.of());

        List<OrderEntity> orders = orderRepository.findForKdsBoard(
                List.of(OrderType.DINE_IN, OrderType.PICKUP),
                List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY),
                effectiveBranchId);

        List<UUID> orderIds = orders.stream().map(OrderEntity::getId).toList();
        Map<UUID, List<OrderItemEntity>> itemsByOrderId = new HashMap<>();
        Map<UUID, List<String>> modifiersByItemId = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(orderIds);
            for (OrderItemEntity item : items) {
                List<OrderItemEntity> bucket = itemsByOrderId.computeIfAbsent(item.getOrder().getId(), ignored -> new ArrayList<>());
                bucket.add(item);
            }
            List<UUID> itemIds = items.stream().map(OrderItemEntity::getId).toList();
            if (!itemIds.isEmpty()) {
                for (OrderItemModifierEntity modifier : orderItemModifierRepository.findByOrderItem_IdInOrderByCreatedAtAsc(itemIds)) {
                    List<String> bucket = modifiersByItemId.computeIfAbsent(modifier.getOrderItem().getId(), ignored -> new ArrayList<>());
                    bucket.add(modifier.getOptionNameSnapshot());
                }
            }
        }

        for (OrderStatus status : List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY)) {
            List<KdsOrderCard> cards = orders.stream()
                    .filter(order -> order.getStatus() == status)
                    .map(order -> toCard(order, itemsByOrderId, modifiersByItemId))
                    .toList();
            columns.put(status, cards);
        }
        return new KdsBoardResponse(columns);
    }

    private UUID resolveEffectiveBranch(UUID requestedBranchId, UserPrincipal principal) {
        if (principal == null) {
            return requestedBranchId;
        }
        if (principal.role() == Role.ADMIN) {
            return requestedBranchId;
        }
        if (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) {
            if (principal.branchId() == null) {
                return requestedBranchId;
            }
            if (requestedBranchId != null && !requestedBranchId.equals(principal.branchId())) {
                throw new ApiException(403, "Forbidden");
            }
            return principal.branchId();
        }
        return requestedBranchId;
    }

    private KdsOrderCard toCard(OrderEntity order,
                                Map<UUID, List<OrderItemEntity>> itemsByOrderId,
                                Map<UUID, List<String>> modifiersByItemId) {
        List<KdsOrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of()).stream()
                .map(item -> {
                    List<String> modifiers = modifiersByItemId.getOrDefault(item.getId(), List.of());
                    return new KdsOrderItem(item.getNameSnapshot(), item.getQuantity(), item.getNotes(), modifiers);
                })
                .toList();
        return new KdsOrderCard(
                order.getId(),
                order.getTable() == null ? "N/A" : order.getTable().getNumber(),
                order.getBranch() == null ? null : order.getBranch().getName(),
                order.getStatus(),
                order.getNotes(),
                order.getCreatedAt(),
                items
        );
    }
}
