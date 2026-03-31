package com.restaurantmanager.core.phase10.runtime;

import com.restaurantmanager.core.phase10.analytics.AnalyticsService;
import com.restaurantmanager.core.phase10.analytics.OrderAnalyticsRecord;
import com.restaurantmanager.core.phase10.analytics.TopItem;
import com.restaurantmanager.core.phase10.load.LoadTestService;
import com.restaurantmanager.core.phase10.security.SecurityGuardService;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import org.springframework.stereotype.Service;

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

    public RevenueResponse revenue(LocalDate from, LocalDate to, RevenuePeriod period) {
        List<OrderAnalyticsRecord> records = analyticsRecords(from, to);
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

    public List<TopItem> topItems(LocalDate from, LocalDate to, int limit) {
        return analyticsService.topItems(analyticsRecords(from, to), limit);
    }

    public List<PeakHourPoint> peakHours(LocalDate from, LocalDate to) {
        return analyticsRecords(from, to).stream()
                .collect(Collectors.groupingBy(OrderAnalyticsRecord::orderHour, Collectors.summingInt(OrderAnalyticsRecord::quantity)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PeakHourPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    public AverageOrderValueResponse averageOrderValue(LocalDate from, LocalDate to) {
        List<OrderEntity> orders = successfulOrders(from, to);
        BigDecimal total = orders.stream().map(OrderEntity::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = orders.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
        return new AverageOrderValueResponse(average, orders.size());
    }

    private List<OrderAnalyticsRecord> analyticsRecords(LocalDate from, LocalDate to) {
        List<OrderEntity> orders = successfulOrders(from, to);
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

    private List<OrderEntity> successfulOrders(LocalDate from, LocalDate to) {
        Instant fromInstant = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to == null ? Instant.now().plusSeconds(86_400) : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<PaymentEntity> payments = paymentRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(PaymentStatus.SUCCESS), toInstant);
        return payments.stream()
                .filter(payment -> payment.getPaidAt() != null)
                .filter(payment -> !payment.getPaidAt().isBefore(fromInstant) && payment.getPaidAt().isBefore(toInstant))
                .map(PaymentEntity::getOrder)
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity(), (left, right) -> left))
                .values().stream()
                .sorted(Comparator.comparing(OrderEntity::getCreatedAt))
                .toList();
    }

    public record AnalyticsSummary(BigDecimal dailyRevenue, List<TopItem> topItems, int peakHour, int repeatCustomers) {}

    public record LoadReport(long orderLatencyMs, long paymentLatencyMs, int deliveredEvents) {}

    public record RevenueResponse(List<RevenuePoint> points) {}

    public record RevenuePoint(String bucket, BigDecimal revenue) {}

    public record PeakHourPoint(int hour, int orders) {}

    public record AverageOrderValueResponse(BigDecimal averageOrderValue, int orderCount) {}

    public enum RevenuePeriod {
        DAY,
        WEEK,
        MONTH
    }
}
