package com.restaurantmanager.core.phase10.runtime;

import com.restaurantmanager.core.phase10.analytics.AnalyticsService;
import com.restaurantmanager.core.phase10.analytics.OrderAnalyticsRecord;
import com.restaurantmanager.core.phase10.analytics.TopItem;
import com.restaurantmanager.core.phase10.load.LoadTestService;
import com.restaurantmanager.core.phase10.security.SecurityGuardService;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class Phase10RuntimeService {
    private static final Instant MAX_FILTER_INSTANT = Instant.parse("9999-12-31T23:59:59Z");

    private final AnalyticsService analyticsService;
    private final LoadTestService loadTestService;
    private final SecurityGuardService securityGuardService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    public Phase10RuntimeService(SecurityGuardService securityGuardService,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 PaymentRepository paymentRepository) {
        this.analyticsService = new AnalyticsService();
        this.loadTestService = new LoadTestService();
        this.securityGuardService = securityGuardService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
    }

    public AnalyticsSummary summarize(List<OrderAnalyticsRecord> records, int topItemLimit) {
        BigDecimal dailyRevenue = analyticsService.dailyRevenue(records);
        List<TopItem> topItems = analyticsService.topItems(records, topItemLimit);
        int peakHour = analyticsService.peakHour(records);
        int repeatCustomers = analyticsService.repeatCustomers(records);
        return new AnalyticsSummary(dailyRevenue, topItems, peakHour, repeatCustomers);
    }

    public LoadReport runLoad(int concurrentRequests, int subscribers) {
        long orderLatencyMs = loadTestService.concurrentOrderCreationLatencyMs(concurrentRequests);
        long paymentLatencyMs = loadTestService.concurrentPaymentInitiationLatencyMs(concurrentRequests);
        int deliveredEvents = loadTestService.broadcastOrderEvent(subscribers);
        return new LoadReport(orderLatencyMs, paymentLatencyMs, deliveredEvents);
    }

    public String sanitizeSearch(String input) {
        return securityGuardService.sanitizeSearch(input);
    }

    public String escapeReceiptNotes(String notes) {
        return securityGuardService.escapeReceiptNotes(notes);
    }

    public boolean canCustomerAccessOrder(UUID requesterId, UUID orderOwnerId) {
        return securityGuardService.canCustomerAccessOrder(requesterId, orderOwnerId);
    }

    @Transactional(readOnly = true)
    public RevenueResponse revenue(LocalDate from, LocalDate to, UUID branchId, RevenuePeriod period) {
        List<OrderAnalyticsRecord> records = analyticsRecords(from, to, branchId);
        Function<OrderAnalyticsRecord, String> bucket = switch (period) {
            case DAY -> record -> record.createdAt().atOffset(ZoneOffset.UTC).toLocalDate().toString();
            case WEEK -> record -> {
                LocalDate date = record.createdAt().atOffset(ZoneOffset.UTC).toLocalDate();
                return date.getYear() + "-W" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            };
            case MONTH -> record -> {
                LocalDate date = record.createdAt().atOffset(ZoneOffset.UTC).toLocalDate();
                return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            };
        };

        List<RevenuePoint> points = records.stream()
                .collect(Collectors.groupingBy(bucket, Collectors.reducing(BigDecimal.ZERO, OrderAnalyticsRecord::lineRevenue, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RevenuePoint(entry.getKey(), entry.getValue()))
                .toList();
        return new RevenueResponse(points);
    }

    @Transactional(readOnly = true)
    public List<TopItem> topItems(LocalDate from, LocalDate to, UUID branchId, int limit) {
        return analyticsService.topItems(analyticsRecords(from, to, branchId), limit);
    }

    @Transactional(readOnly = true)
    public List<PeakHourPoint> peakHours(LocalDate from, LocalDate to, UUID branchId) {
        return analyticsRecords(from, to, branchId).stream()
                .collect(Collectors.groupingBy(OrderAnalyticsRecord::orderHour, Collectors.summingInt(OrderAnalyticsRecord::quantity)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PeakHourPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AverageOrderValueResponse averageOrderValue(LocalDate from, LocalDate to, UUID branchId) {
        List<OrderEntity> orders = successfulOrders(from, to, branchId);
        BigDecimal total = orders.stream().map(OrderEntity::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = orders.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
        return new AverageOrderValueResponse(average, orders.size());
    }

    @Transactional(readOnly = true)
    public AnalyticsOverview overview(LocalDate from, LocalDate to, UUID branchId, RevenuePeriod period) {
        List<OrderEntity> paidOrders = successfulOrders(from, to, branchId);
        List<OrderEntity> visibleOrders = visibleOrders(from, to, branchId);
        List<PaymentEntity> successfulPayments = successfulPayments(from, to, branchId);
        List<OrderAnalyticsRecord> records = analyticsRecords(paidOrders);

        BigDecimal totalRevenue = successfulPayments.stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int paidOrderCount = paidOrders.size();
        BigDecimal averageOrderValue = paidOrderCount == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP);

        return new AnalyticsOverview(
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                paidOrderCount,
                averageOrderValue,
                analyticsService.repeatCustomers(records),
                revenue(from, to, branchId, period),
                analyticsService.topItems(records, 5),
                peakHours(from, to, branchId),
                branchBreakdown(paidOrders, successfulPayments),
                paymentMethodBreakdown(successfulPayments),
                orderTypeBreakdown(paidOrders),
                orderStatusBreakdown(visibleOrders)
        );
    }

    private List<OrderAnalyticsRecord> analyticsRecords(LocalDate from, LocalDate to, UUID branchId) {
        return analyticsRecords(successfulOrders(from, to, branchId));
    }

    private List<OrderAnalyticsRecord> analyticsRecords(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrderEntity> ordersById = orders.stream().collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(ordersById.keySet());
        List<OrderAnalyticsRecord> records = new ArrayList<>();
        for (OrderItemEntity item : items) {
            OrderEntity order = ordersById.get(item.getOrder().getId());
            if (order == null) {
                continue;
            }
            records.add(new OrderAnalyticsRecord(
                    order.getId(),
                    order.getCustomerUserId(),
                    item.getMenuItem().getId(),
                    item.getNameSnapshot(),
                    item.getQuantity(),
                    item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())),
                    order.getCreatedAt().atOffset(ZoneOffset.UTC).getHour(),
                    order.getCreatedAt(),
                    true
            ));
        }
        records.sort(Comparator.comparing(OrderAnalyticsRecord::createdAt));
        return records;
    }

    private List<OrderEntity> successfulOrders(LocalDate from, LocalDate to, UUID branchId) {
        return successfulPayments(from, to, branchId).stream()
                .map(PaymentEntity::getOrder)
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity(), (left, right) -> left))
                .values().stream()
                .sorted(Comparator.comparing(OrderEntity::getCreatedAt))
                .toList();
    }

    private List<PaymentEntity> successfulPayments(LocalDate from, LocalDate to, UUID branchId) {
        Instant fromInstant = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to == null ? Instant.now().plusSeconds(86_400) : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return paymentRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(PaymentStatus.SUCCESS), toInstant)
                .stream()
                .filter(payment -> payment.getPaidAt() != null)
                .filter(payment -> !payment.getPaidAt().isBefore(fromInstant) && payment.getPaidAt().isBefore(toInstant))
                .filter(payment -> branchId == null
                        || (payment.getOrder().getBranch() != null && branchId.equals(payment.getOrder().getBranch().getId())))
                .sorted(Comparator.comparing(PaymentEntity::getPaidAt))
                .toList();
    }

    private List<OrderEntity> visibleOrders(LocalDate from, LocalDate to, UUID branchId) {
        Instant fromInstant = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to == null ? MAX_FILTER_INSTANT : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return orderRepository.findVisibleOrders(null, branchId, null, null, fromInstant, toInstant);
    }

    private List<BranchPerformancePoint> branchBreakdown(List<OrderEntity> paidOrders, List<PaymentEntity> successfulPayments) {
        Map<UUID, BigDecimal> revenueByBranch = successfulPayments.stream()
                .filter(payment -> payment.getOrder().getBranch() != null)
                .collect(Collectors.groupingBy(payment -> payment.getOrder().getBranch().getId(),
                        Collectors.reducing(BigDecimal.ZERO, PaymentEntity::getAmount, BigDecimal::add)));
        Map<UUID, Long> ordersByBranch = paidOrders.stream()
                .filter(order -> order.getBranch() != null)
                .collect(Collectors.groupingBy(order -> order.getBranch().getId(), Collectors.counting()));

        return paidOrders.stream()
                .filter(order -> order.getBranch() != null)
                .collect(Collectors.toMap(order -> order.getBranch().getId(), Function.identity(), (left, right) -> left))
                .values().stream()
                .map(order -> {
                    UUID id = order.getBranch().getId();
                    BigDecimal revenue = revenueByBranch.getOrDefault(id, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    int count = ordersByBranch.getOrDefault(id, 0L).intValue();
                    BigDecimal average = count == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                    return new BranchPerformancePoint(id, order.getBranch().getCode(), order.getBranch().getName(), revenue, count, average);
                })
                .sorted(Comparator.comparing(BranchPerformancePoint::revenue).reversed())
                .toList();
    }

    private List<PaymentMethodBreakdownPoint> paymentMethodBreakdown(List<PaymentEntity> successfulPayments) {
        return successfulPayments.stream()
                .collect(Collectors.groupingBy(PaymentEntity::getMethod))
                .entrySet().stream()
                .map(entry -> new PaymentMethodBreakdownPoint(
                        entry.getKey(),
                        entry.getValue().stream().map(PaymentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(PaymentMethodBreakdownPoint::revenue).reversed())
                .toList();
    }

    private List<OrderTypeBreakdownPoint> orderTypeBreakdown(List<OrderEntity> paidOrders) {
        return paidOrders.stream()
                .collect(Collectors.groupingBy(OrderEntity::getType))
                .entrySet().stream()
                .map(entry -> new OrderTypeBreakdownPoint(
                        entry.getKey(),
                        entry.getValue().stream().map(OrderEntity::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(OrderTypeBreakdownPoint::revenue).reversed())
                .toList();
    }

    private List<OrderStatusBreakdownPoint> orderStatusBreakdown(List<OrderEntity> visibleOrders) {
        return visibleOrders.stream()
                .collect(Collectors.groupingBy(OrderEntity::getStatus, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new OrderStatusBreakdownPoint(entry.getKey(), entry.getValue().intValue()))
                .sorted(Comparator.comparing(OrderStatusBreakdownPoint::orderCount).reversed())
                .toList();
    }

    public record AnalyticsSummary(BigDecimal dailyRevenue, List<TopItem> topItems, int peakHour, int repeatCustomers) {}

    public record LoadReport(long orderLatencyMs, long paymentLatencyMs, int deliveredEvents) {}

    public record RevenueResponse(List<RevenuePoint> points) {}

    public record RevenuePoint(String bucket, BigDecimal revenue) {}

    public record PeakHourPoint(int hour, int orders) {}

    public record AverageOrderValueResponse(BigDecimal averageOrderValue, int orderCount) {}

    public record AnalyticsOverview(
            BigDecimal totalRevenue,
            int paidOrderCount,
            BigDecimal averageOrderValue,
            int repeatCustomers,
            RevenueResponse revenue,
            List<TopItem> topItems,
            List<PeakHourPoint> peakHours,
            List<BranchPerformancePoint> branches,
            List<PaymentMethodBreakdownPoint> paymentMethods,
            List<OrderTypeBreakdownPoint> orderTypes,
            List<OrderStatusBreakdownPoint> orderStatuses
    ) {}

    public record BranchPerformancePoint(
            UUID branchId,
            String branchCode,
            String branchName,
            BigDecimal revenue,
            int orderCount,
            BigDecimal averageOrderValue
    ) {}

    public record PaymentMethodBreakdownPoint(
            PaymentMethod method,
            BigDecimal revenue,
            int paymentCount
    ) {}

    public record OrderTypeBreakdownPoint(
            OrderType type,
            BigDecimal revenue,
            int orderCount
    ) {}

    public record OrderStatusBreakdownPoint(
            OrderStatus status,
            int orderCount
    ) {}

    public enum RevenuePeriod {
        DAY,
        WEEK,
        MONTH
    }
}
