package com.restaurantmanager.core.table.payment;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.servicerequest.ServiceRequestEntity;
import com.restaurantmanager.core.servicerequest.ServiceRequestRepository;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which tables still owe money, and which of those need a member of staff now. Guests are never prompted: a table is
 * "at risk" when everything has been served and nothing was paid for a while, or the bill was asked for and is still
 * unpaid, and staff then go to the table with the bill.
 */
@Service
public class TablePaymentWatchService {
    /** Why a table needs attention. */
    public enum Risk { NONE, SERVED_UNPAID, BILL_WAITING }

    public record TableWatch(UUID tableId, String tableNumber, UUID branchId, String branchName,
                             BigDecimal outstanding, int openOrders, boolean allServed, Instant lastServedAt,
                             Instant billRequestedAt, Instant firstOrderAt, Risk risk, long minutesWaiting) {}

    /** Orders older than this are not looked at (an open table is never this old). */
    private static final Duration LOOKBACK = Duration.ofHours(36);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final int servedUnpaidMinutes;
    private final int billWaitingMinutes;

    public TablePaymentWatchService(OrderRepository orderRepository,
                                    PaymentRepository paymentRepository,
                                    ServiceRequestRepository serviceRequestRepository,
                                    @Value("${tables.payment-watch.served-unpaid-minutes:20}") int servedUnpaidMinutes,
                                    @Value("${tables.payment-watch.bill-waiting-minutes:10}") int billWaitingMinutes) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.servedUnpaidMinutes = servedUnpaidMinutes;
        this.billWaitingMinutes = billWaitingMinutes;
    }

    /** Tables with money owed in the viewer's branch (or the requested one), at-risk tables first. */
    @Transactional(readOnly = true)
    public List<TableWatch> watch(UUID requestedBranchId, UserPrincipal principal) {
        UUID branchId = scope(requestedBranchId, principal);
        Instant now = Instant.now();

        List<OrderEntity> orders = orderRepository.findOpenDineInSince(now.minus(LOOKBACK)).stream()
                .filter(o -> branchId == null || (o.getTable().getBranch() != null && branchId.equals(o.getTable().getBranch().getId())))
                .toList();
        Map<UUID, BigDecimal> paid = new HashMap<>();
        if (!orders.isEmpty()) {
            paymentRepository.findByOrderIdInAndStatus(orders.stream().map(OrderEntity::getId).toList(), PaymentStatus.SUCCESS)
                    .forEach(p -> paid.merge(p.getOrder().getId(), p.getAmount(), BigDecimal::add));
        }

        Map<UUID, List<OrderEntity>> owedByTable = new LinkedHashMap<>();
        for (OrderEntity order : orders) {
            if (paid.getOrDefault(order.getId(), BigDecimal.ZERO).compareTo(order.getTotal()) < 0) {
                owedByTable.computeIfAbsent(order.getTable().getId(), ignored -> new ArrayList<>()).add(order);
            }
        }

        Map<UUID, Instant> billRequested = new HashMap<>();
        for (ServiceRequestEntity request : serviceRequestRepository.findByTypeAndStatusIn(ServiceRequestEntity.Type.BILL,
                EnumSet.of(ServiceRequestEntity.Status.OPEN, ServiceRequestEntity.Status.ACKNOWLEDGED))) {
            billRequested.merge(request.getTable().getId(), request.getCreatedAt(), (a, b) -> a.isBefore(b) ? a : b);
        }

        List<TableWatch> result = new ArrayList<>();
        for (List<OrderEntity> owed : owedByTable.values()) {
            RestaurantTableEntity table = owed.get(0).getTable();
            BigDecimal outstanding = owed.stream()
                    .map(o -> o.getTotal().subtract(paid.getOrDefault(o.getId(), BigDecimal.ZERO)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean allServed = owed.stream().allMatch(o -> o.getStatus() == OrderStatus.COMPLETED);
            Instant lastServedAt = allServed
                    ? owed.stream().map(o -> o.getServedAt() == null ? o.getUpdatedAt() : o.getServedAt()).max(Comparator.naturalOrder()).orElse(null)
                    : null;
            Instant billAt = billRequested.get(table.getId());
            Instant firstOrderAt = owed.stream().map(OrderEntity::getCreatedAt).min(Comparator.naturalOrder()).orElse(null);
            result.add(assess(table, outstanding, owed.size(), allServed, lastServedAt, billAt, firstOrderAt, now));
        }
        result.sort(Comparator.comparing((TableWatch w) -> w.risk() == Risk.NONE)
                .thenComparing(TableWatch::minutesWaiting, Comparator.reverseOrder())
                .thenComparing(TableWatch::tableNumber));
        return result;
    }

    TableWatch assess(RestaurantTableEntity table, BigDecimal outstanding, int openOrders, boolean allServed,
                      Instant lastServedAt, Instant billAt, Instant firstOrderAt, Instant now) {
        long billMinutes = billAt == null ? -1 : Duration.between(billAt, now).toMinutes();
        long servedMinutes = lastServedAt == null ? -1 : Duration.between(lastServedAt, now).toMinutes();
        Risk risk = Risk.NONE;
        long waiting = Math.max(Math.max(billMinutes, servedMinutes), 0);
        if (billAt != null && billMinutes >= billWaitingMinutes) {
            risk = Risk.BILL_WAITING;
            waiting = billMinutes;
        } else if (allServed && lastServedAt != null && servedMinutes >= servedUnpaidMinutes) {
            risk = Risk.SERVED_UNPAID;
            waiting = servedMinutes;
        }
        return new TableWatch(table.getId(), table.getNumber(),
                table.getBranch() == null ? null : table.getBranch().getId(),
                table.getBranch() == null ? null : table.getBranch().getName(),
                outstanding, openOrders, allServed, lastServedAt, billAt, firstOrderAt, risk, waiting);
    }

    /** Branch staff see their branch; admins and staff without a branch see the requested branch or all. */
    static UUID scope(UUID requested, UserPrincipal principal) {
        boolean branchScoped = (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null;
        if (branchScoped) {
            if (requested != null && !requested.equals(principal.branchId())) {
                throw new ApiException(403, "Forbidden");
            }
            return principal.branchId();
        }
        return requested;
    }
}
