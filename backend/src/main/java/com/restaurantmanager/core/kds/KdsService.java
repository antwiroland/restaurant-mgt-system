package com.restaurantmanager.core.kds;

import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemModifierEntity;
import com.restaurantmanager.core.order.OrderItemModifierRepository;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
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
    public KdsBoardResponse board(UUID branchId) {
        Map<OrderStatus, List<KdsOrderCard>> columns = new EnumMap<>(OrderStatus.class);
        columns.put(OrderStatus.CONFIRMED, List.of());
        columns.put(OrderStatus.PREPARING, List.of());
        columns.put(OrderStatus.READY, List.of());

        List<OrderEntity> orders = orderRepository.findAll().stream()
                .filter(order -> order.getType() == OrderType.DINE_IN || order.getType() == OrderType.PICKUP)
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED
                        || order.getStatus() == OrderStatus.PREPARING
                        || order.getStatus() == OrderStatus.READY)
                .filter(order -> branchId == null
                        || (order.getBranch() != null && order.getBranch().getId().equals(branchId)))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();

        for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY)) {
            List<KdsOrderCard> cards = orders.stream()
                    .filter(order -> order.getStatus() == status)
                    .map(this::toCard)
                    .toList();
            columns.put(status, cards);
        }
        return new KdsBoardResponse(columns);
    }

    private KdsOrderCard toCard(OrderEntity order) {
        List<KdsOrderItem> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(item -> {
                    List<String> modifiers = orderItemModifierRepository.findByOrderItem_IdOrderByCreatedAtAsc(item.getId()).stream()
                            .map(OrderItemModifierEntity::getOptionNameSnapshot)
                            .toList();
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
