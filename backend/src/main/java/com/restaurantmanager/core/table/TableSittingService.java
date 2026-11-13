package com.restaurantmanager.core.table;

import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A sitting is one party's use of a table: from their first order until the bill is settled and the food served
 * (or staff close, reverse or write off the table). Guest QR pages (running bill, order tracking, waiter calls)
 * only show the current sitting, so the next party at the table never sees what the previous one ordered or paid.
 *
 * <p>The boundary is stored on the table ({@code guest_history_from}). It is moved forward when a sitting ends and,
 * as a safety net, when an order arrives at a table with nothing open.
 */
@Service
public class TableSittingService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RestaurantTableRepository tableRepository;

    public TableSittingService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                               RestaurantTableRepository tableRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.tableRepository = tableRepository;
    }

    /** Staff ended the sitting (closed, reversed or walked-out table, or set it free): hide everything so far. */
    public void endSitting(RestaurantTableEntity table) {
        table.setGuestHistoryFrom(Instant.now());
        tableRepository.save(table);
    }

    /** Ends the sitting if every order on the table is paid and served, cancelled or written off. */
    public void endSittingIfFinished(UUID tableId) {
        if (tableId == null) {
            return;
        }
        tableRepository.findById(tableId).ifPresent(table -> {
            List<OrderEntity> current = sittingOrders(table, orderRepository.findByTableIdOrderByCreatedAtAsc(tableId));
            if (!current.isEmpty() && noneOpen(current)) {
                endSitting(table);
            }
        });
    }

    /** Called before a dine-in order is placed: if nothing is open at the table, this order starts a new sitting. */
    public void startSittingIfIdle(RestaurantTableEntity table) {
        if (table.getId() == null) {
            return;
        }
        List<OrderEntity> orders = orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId());
        if (noneOpen(orders)) {
            table.setGuestHistoryFrom(Instant.now());
        }
    }

    /**
     * The orders a guest at the table may see: those of the current sitting, or none when the table has nothing open
     * (the previous party has finished and the next has not ordered yet).
     */
    public List<OrderEntity> visibleToGuests(RestaurantTableEntity table, List<OrderEntity> tableOrders) {
        List<OrderEntity> current = sittingOrders(table, tableOrders);
        return noneOpen(current) ? List.of() : current;
    }

    /**
     * The moment from which guest-facing records (such as waiter calls) belong to the current sitting. A party that has
     * just sat down may call the waiter before ordering, so this is the end of the previous sitting, not the first order.
     */
    public Instant guestHistoryFrom(RestaurantTableEntity table) {
        if (table.getGuestHistoryFrom() != null) {
            return table.getGuestHistoryFrom();
        }
        // Tables from before sittings were tracked: start at the oldest order still open, or else after the last party's
        // orders were last touched (served, paid, cancelled).
        List<OrderEntity> orders = orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId()).stream()
                .filter(o -> o.getType() == OrderType.DINE_IN).toList();
        List<OrderEntity> open = openOrders(orders);
        if (!open.isEmpty()) {
            return open.get(0).getCreatedAt();
        }
        return orders.stream().map(OrderEntity::getUpdatedAt).filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(Instant.EPOCH);
    }

    private List<OrderEntity> sittingOrders(RestaurantTableEntity table, List<OrderEntity> tableOrders) {
        List<OrderEntity> dineIn = tableOrders.stream().filter(o -> o.getType() == OrderType.DINE_IN).toList();
        Instant from = table.getGuestHistoryFrom();
        if (from == null) {
            List<OrderEntity> open = openOrders(dineIn);
            if (open.isEmpty()) {
                return List.of();
            }
            from = open.get(0).getCreatedAt();
        }
        Instant start = from;
        return dineIn.stream().filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(start)).toList();
    }

    private boolean noneOpen(List<OrderEntity> orders) {
        return openOrders(orders).isEmpty();
    }

    /** Open = still owes money or still with the kitchen/waiter. Cancelled and written-off orders are closed. */
    private List<OrderEntity> openOrders(List<OrderEntity> orders) {
        List<OrderEntity> candidates = orders.stream()
                .filter(o -> o.getType() == OrderType.DINE_IN)
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && !o.isWrittenOff())
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> paid = new HashMap<>();
        paymentRepository.findByOrderIdInAndStatus(candidates.stream().map(OrderEntity::getId).toList(), PaymentStatus.SUCCESS)
                .forEach(p -> paid.merge(p.getOrder().getId(), p.getAmount(), BigDecimal::add));
        return candidates.stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED
                        || paid.getOrDefault(o.getId(), BigDecimal.ZERO).compareTo(o.getTotal()) < 0)
                .toList();
    }
}
