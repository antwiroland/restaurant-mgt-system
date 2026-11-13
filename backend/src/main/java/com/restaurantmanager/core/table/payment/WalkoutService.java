package com.restaurantmanager.core.table.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.common.AfterCommit;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRealtimePublisher;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.reporting.CsvWriter;
import com.restaurantmanager.core.reporting.PdfTableDocument;
import com.restaurantmanager.core.reporting.ReportService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.table.TableRealtimePublisher;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.table.qr.TableSessionService;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Records a walkout: a manager closes out a table whose guests left without paying. The unpaid orders are written
 * off (they stop blocking the table and leave the kitchen screen), the table is freed, and the loss is recorded with
 * who took the orders and any phone the guests left, for follow-up and the walkout report.
 */
@Service
public class WalkoutService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String QR_GUEST = "QR (guest)";

    public record WalkoutRequest(WalkoutEntity.Reason reason, String note) {}

    public record WalkoutView(UUID id, UUID tableId, String tableNumber, String branchName, BigDecimal amountOwed,
                              int orderCount, WalkoutEntity.Reason reason, String note, String orderTakenBy,
                              String guestPhones, String recordedByName, Instant createdAt) {
        static WalkoutView from(WalkoutEntity w) {
            return new WalkoutView(w.getId(), w.getTableId(), w.getTableNumber(), w.getBranchName(), w.getAmountOwed(),
                    w.getOrderCount(), w.getReason(), w.getNote(), w.getOrderTakenBy(), w.getGuestPhones(),
                    w.getRecordedByName(), w.getCreatedAt());
        }
    }

    private final WalkoutRepository walkoutRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final TableSessionService tableSessionService;
    private final com.restaurantmanager.core.table.TableSittingService tableSitting;
    private final TableRealtimePublisher tableRealtimePublisher;
    private final OrderRealtimePublisher orderRealtimePublisher;
    private final CacheManager cacheManager;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ZoneId zone;

    public WalkoutService(WalkoutRepository walkoutRepository, RestaurantTableRepository tableRepository,
                          OrderRepository orderRepository, PaymentRepository paymentRepository,
                          UserRepository userRepository, TableSessionService tableSessionService, com.restaurantmanager.core.table.TableSittingService tableSitting,
                          TableRealtimePublisher tableRealtimePublisher, OrderRealtimePublisher orderRealtimePublisher,
                          CacheManager cacheManager, AuditService auditService, ObjectMapper objectMapper,
                          @Value("${app.time-zone:Africa/Accra}") String timeZone) {
        this.walkoutRepository = walkoutRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.tableSessionService = tableSessionService;
        this.tableSitting = tableSitting;
        this.tableRealtimePublisher = tableRealtimePublisher;
        this.orderRealtimePublisher = orderRealtimePublisher;
        this.cacheManager = cacheManager;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional
    public WalkoutView record(UUID tableId, WalkoutRequest request, UserPrincipal principal) {
        if (principal.role() != Role.ADMIN && principal.role() != Role.MANAGER) {
            throw new ApiException(403, "Only a manager can record a walkout");
        }
        if (request == null || request.reason() == null) {
            throw new ApiException(400, "Choose a reason");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (note != null && note.length() > 500) {
            throw new ApiException(400, "Keep the note under 500 characters");
        }
        if (request.reason() == WalkoutEntity.Reason.OTHER && note == null) {
            throw new ApiException(400, "Say what happened");
        }
        RestaurantTableEntity table = tableRepository.findById(tableId).orElseThrow(() -> new ApiException(404, "Table not found"));
        if (principal.role() == Role.MANAGER && principal.branchId() != null
                && (table.getBranch() == null || !principal.branchId().equals(table.getBranch().getId()))) {
            throw new ApiException(403, "Forbidden");
        }

        List<OrderEntity> tableOrders = orderRepository.findByTableIdOrderByCreatedAtAsc(tableId).stream()
                .filter(o -> o.getType() == OrderType.DINE_IN && o.getStatus() != OrderStatus.CANCELLED && !o.isWrittenOff())
                .toList();
        Map<UUID, BigDecimal> paid = new HashMap<>();
        if (!tableOrders.isEmpty()) {
            paymentRepository.findByOrderIdInAndStatus(tableOrders.stream().map(OrderEntity::getId).toList(), PaymentStatus.SUCCESS)
                    .forEach(p -> paid.merge(p.getOrder().getId(), p.getAmount(), BigDecimal::add));
        }
        List<OrderEntity> unpaid = tableOrders.stream()
                .filter(o -> paid.getOrDefault(o.getId(), BigDecimal.ZERO).compareTo(o.getTotal()) < 0)
                .toList();
        if (unpaid.isEmpty()) {
            throw new ApiException(409, "Nothing is owed on this table");
        }

        BigDecimal owed = unpaid.stream()
                .map(o -> o.getTotal().subtract(paid.getOrDefault(o.getId(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        UserEntity recorder = userRepository.findById(principal.userId()).orElse(null);

        WalkoutEntity walkout = new WalkoutEntity();
        walkout.setTableId(table.getId());
        walkout.setTableNumber(table.getNumber());
        walkout.setBranchId(table.getBranch() == null ? null : table.getBranch().getId());
        walkout.setBranchName(table.getBranch() == null ? null : table.getBranch().getName());
        walkout.setAmountOwed(owed);
        walkout.setOrderCount(unpaid.size());
        walkout.setReason(request.reason());
        walkout.setNote(note);
        walkout.setOrderTakenBy(truncate(orderTakers(unpaid), 500));
        walkout.setGuestPhones(truncate(String.join(", ", unpaid.stream().map(OrderEntity::getGuestPhone)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))), 200));
        walkout.setFirstOrderAt(unpaid.get(0).getCreatedAt());
        walkout.setRecordedBy(principal.userId());
        walkout.setRecordedByName(recorder == null ? null : recorder.getName());
        WalkoutEntity saved = walkoutRepository.save(walkout);

        for (OrderEntity order : unpaid) {
            order.setWalkoutId(saved.getId());
            OrderStatus previous = order.getStatus();
            if (previous != OrderStatus.COMPLETED) {
                // Close it out so it leaves the kitchen screen; the food is not returned to stock.
                order.setStatus(OrderStatus.COMPLETED);
                if (order.getServedAt() == null) {
                    order.setServedAt(Instant.now());
                }
                orderRealtimePublisher.publishOrderStatusChanged(order.getId(), previous, OrderStatus.COMPLETED, table.getQrToken());
            }
            orderRepository.save(order);
        }

        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);
        tableSitting.endSitting(table);
        tableSessionService.closeAll(table.getId());
        AfterCommit.run(this::evictTableCaches);
        tableRealtimePublisher.publishTableStatusChanged(table.getNumber(), TableStatus.AVAILABLE);

        auditService.log(recorder, AuditAction.WALKOUT_RECORDED, "TABLE_WALKOUT", saved.getId(), metadata(saved), null);
        return WalkoutView.from(saved);
    }

    /** Walkouts in a period as CSV or PDF, with totals by who took the orders. Managers see their branch only. */
    @Transactional(readOnly = true)
    public ReportService.Report report(LocalDate from, LocalDate to, UUID requestedBranchId, UserPrincipal principal,
                                       ReportService.Format format) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(400, "Choose a valid period");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new ApiException(400, "Choose a period of a year or less");
        }
        UUID branchId = requestedBranchId;
        if (principal.role() == Role.MANAGER && principal.branchId() != null) {
            if (requestedBranchId != null && !requestedBranchId.equals(principal.branchId())) {
                throw new ApiException(403, "Forbidden");
            }
            branchId = principal.branchId();
        }
        final UUID scope = branchId;
        List<WalkoutEntity> walkouts = walkoutRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .filter(w -> scope == null || scope.equals(w.getBranchId()))
                .toList();

        String[] header = {"Recorded", "Branch", "Table", "Owed (GHS)", "Orders", "Reason", "Note", "Order taken by", "Guest phone", "Recorded by"};
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byStaff = new LinkedHashMap<>();
        Map<String, Integer> countByStaff = new HashMap<>();
        for (WalkoutEntity w : walkouts) {
            total = total.add(w.getAmountOwed());
            String takers = w.getOrderTakenBy() == null || w.getOrderTakenBy().isBlank() ? QR_GUEST : w.getOrderTakenBy();
            for (String person : takers.split(", ")) {
                byStaff.merge(person, w.getAmountOwed(), BigDecimal::add);
                countByStaff.merge(person, 1, Integer::sum);
            }
            rows.add(List.of(
                    STAMP.format(w.getCreatedAt().atZone(zone)),
                    w.getBranchName() == null ? "" : w.getBranchName(),
                    w.getTableNumber(),
                    w.getAmountOwed(),
                    w.getOrderCount(),
                    humanize(w.getReason()),
                    w.getNote() == null ? "" : w.getNote(),
                    takers,
                    w.getGuestPhones() == null ? "" : w.getGuestPhones(),
                    w.getRecordedByName() == null ? "" : w.getRecordedByName()));
        }

        String name = "walkouts_" + from + "_" + to;
        if (format == ReportService.Format.CSV) {
            CsvWriter csv = new CsvWriter().row(List.of((Object[]) header));
            rows.forEach(csv::row);
            return new ReportService.Report(name + ".csv", "text/csv; charset=utf-8", csv.toBytes());
        }
        PdfTableDocument pdf = new PdfTableDocument("Walkouts", true)
                .subtitle("Period: " + from + " to " + to + (scope == null ? "" : "  |  Branch filter applied"))
                .subtitle("Generated " + STAMP.format(Instant.now().atZone(zone)))
                .summary("Walkouts", String.valueOf(walkouts.size()))
                .summary("Lost (GHS)", total.toPlainString());
        byStaff.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> pdf.summary("Orders taken by " + e.getKey(),
                        countByStaff.get(e.getKey()) + " · GHS " + e.getValue().toPlainString()));
        pdf.columns(header, new double[]{1.1, 1.0, 0.6, 0.8, 0.6, 1.0, 1.8, 1.4, 1.1, 1.0});
        rows.forEach(pdf::row);
        return new ReportService.Report(name + ".pdf", "application/pdf", pdf.toBytes());
    }

    private String orderTakers(List<OrderEntity> orders) {
        Set<String> names = new LinkedHashSet<>();
        for (OrderEntity order : orders) {
            if (order.getCreatedByUserId() == null) {
                names.add(QR_GUEST);
            } else {
                names.add(userRepository.findById(order.getCreatedByUserId()).map(UserEntity::getName).orElse("Unknown staff"));
            }
        }
        return String.join(", ", names);
    }

    private String metadata(WalkoutEntity w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("table", w.getTableNumber());
        m.put("branch", w.getBranchName());
        m.put("amountOwed", w.getAmountOwed());
        m.put("orders", w.getOrderCount());
        m.put("reason", w.getReason().name());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }

    private void evictTableCaches() {
        for (String cacheName : List.of(CacheConfig.TABLES, CacheConfig.TABLE_SCAN)) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }

    private static String humanize(WalkoutEntity.Reason reason) {
        return switch (reason) {
            case WALKED_OUT -> "Walked out";
            case REFUSED_TO_PAY -> "Refused to pay";
            case OTHER -> "Other";
        };
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
