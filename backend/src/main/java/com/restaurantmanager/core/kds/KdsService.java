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
    private final com.restaurantmanager.core.dining.DiningOrderCourseRepository diningCourses;

    public KdsService(OrderRepository orderRepository,
                      OrderItemRepository orderItemRepository,
                      OrderItemModifierRepository orderItemModifierRepository,
                      com.restaurantmanager.core.dining.DiningOrderCourseRepository diningCourses) {
        this.diningCourses = diningCourses;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemModifierRepository = orderItemModifierRepository;
    }

    @Transactional(readOnly = true)
    public KdsBoardResponse board(UUID branchId, UserPrincipal principal) {
        return board(branchId, null, principal);
    }

    /**
     * The kitchen board. Held course orders (not yet released by a waiter or cashier) never appear: only PENDING and
     * later statuses are read. {@code station} (e.g. BAR) shows only that station's tickets; "KITCHEN" also shows
     * tickets with no station.
     */
    @Transactional(readOnly = true)
    public KdsBoardResponse board(UUID branchId, String station, UserPrincipal principal) {
        UUID effectiveBranchId = resolveEffectiveBranch(branchId, principal);
        Map<OrderStatus, List<KdsOrderCard>> columns = new EnumMap<>(OrderStatus.class);
        columns.put(OrderStatus.PENDING, List.of());
        columns.put(OrderStatus.CONFIRMED, List.of());
        columns.put(OrderStatus.PREPARING, List.of());
        columns.put(OrderStatus.READY, List.of());

        // Pickup is paid up front: an unpaid (PENDING) pickup order is not the kitchen's yet.
        List<OrderEntity> orders = orderRepository.findForKdsBoard(
                List.of(OrderType.DINE_IN, OrderType.PICKUP),
                List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY),
                effectiveBranchId).stream()
                .filter(order -> !awaitingPrepayment(order))
                .filter(order -> atStation(order, station))
                .toList();
        Map<UUID, com.restaurantmanager.core.dining.DiningOrderCourseEntity> courses = new HashMap<>();
        List<UUID> courseIds = orders.stream().map(OrderEntity::getDiningOrderCourseId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (!courseIds.isEmpty()) {
            diningCourses.findAllById(courseIds).forEach(course -> courses.put(course.getId(), course));
        }

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
                    .map(order -> toCard(order, itemsByOrderId, modifiersByItemId, courses))
                    .toList();
            columns.put(status, cards);
        }
        return new KdsBoardResponse(columns);
    }

    static boolean atStation(OrderEntity order, String station) {
        if (station == null || station.isBlank()) {
            return true;
        }
        String wanted = station.trim().toUpperCase(java.util.Locale.ROOT);
        String actual = order.getKitchenStation() == null ? "KITCHEN" : order.getKitchenStation();
        return wanted.equals(actual);
    }

    static boolean awaitingPrepayment(OrderEntity order) {
        return order.getType() == OrderType.PICKUP && order.getStatus() == OrderStatus.PENDING;
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
                                Map<UUID, List<String>> modifiersByItemId,
                                Map<UUID, com.restaurantmanager.core.dining.DiningOrderCourseEntity> courses) {
        com.restaurantmanager.core.dining.DiningOrderCourseEntity course =
                order.getDiningOrderCourseId() == null ? null : courses.get(order.getDiningOrderCourseId());
        List<KdsOrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of()).stream()
                .map(item -> {
                    List<String> modifiers = modifiersByItemId.getOrDefault(item.getId(), List.of());
                    return new KdsOrderItem(item.getId(), item.getNameSnapshot(), item.getQuantity(), item.getNotes(), modifiers,
                            item.getReadyAt() != null);
                })
                .toList();
        return new KdsOrderCard(
                order.getId(),
                order.getTable() == null ? "N/A" : order.getTable().getNumber(),
                order.getBranch() == null ? null : order.getBranch().getName(),
                order.getStatus(),
                order.getNotes(),
                order.getCreatedAt(),
                items,
                order.getType(),
                order.getKitchenStation(),
                course == null ? null : course.getCourseName(),
                order.getDiningOrderId(),
                order.getDiningOrderCourseId(),
                course == null ? null : course.getReleasedByName(),
                order.getReleasedAt()
        );
    }
}
