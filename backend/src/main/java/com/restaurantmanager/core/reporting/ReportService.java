package com.restaurantmanager.core.reporting;

import com.restaurantmanager.core.audit.AuditLogEntity;
import com.restaurantmanager.core.audit.AuditLogRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.financial.FinancialService;
import com.restaurantmanager.core.financial.dto.FinancialDtos;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.workforce.WorkforceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds downloadable CSV / PDF reports. */
@Service
public class ReportService {
    public enum Format { CSV, PDF }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final FinancialService financialService;
    private final WorkforceService workforceService;
    private final ZoneId zone;

    public ReportService(PaymentRepository paymentRepository,
                         AuditLogRepository auditLogRepository,
                         FinancialService financialService,
                         WorkforceService workforceService,
                         @org.springframework.beans.factory.annotation.Value("${app.time-zone:Africa/Accra}") String timeZone) {
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.financialService = financialService;
        this.workforceService = workforceService;
        this.zone = ZoneId.of(timeZone);
    }

    public record Report(String filename, String contentType, byte[] content) {}

    // ------------------------------------------------------------ sales

    @Transactional(readOnly = true)
    public Report sales(LocalDate from, LocalDate to, UUID branchId, UserPrincipal principal, Format format) {
        validateRange(from, to);
        UUID branch = scopedBranch(branchId, principal);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<PaymentEntity> payments = paymentRepository.findPaidBetween(List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED), start, end)
                .stream()
                .filter(p -> branch == null || (p.getOrder().getBranch() != null && branch.equals(p.getOrder().getBranch().getId())))
                .toList();
        Map<UUID, List<PaymentEntity>> byOrder = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getOrder().getId(), LinkedHashMap::new, Collectors.toList()));

        String[] header = {"Paid at", "Order", "Type", "Branch", "Subtotal", "Discounts", "Service", "Tax", "Delivery", "Tip", "Total", "Paid", "Refunded", "Method"};
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        for (List<PaymentEntity> orderPayments : byOrder.values()) {
            OrderEntity order = orderPayments.get(0).getOrder();
            BigDecimal paid = orderPayments.stream().map(PaymentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal refunded = orderPayments.stream().map(PaymentEntity::getRefundedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPaid = totalPaid.add(paid);
            totalRefunded = totalRefunded.add(refunded);
            totalTax = totalTax.add(order.getTaxTotal());
            Instant paidAt = orderPayments.stream().map(PaymentEntity::getPaidAt).min(Instant::compareTo).orElse(null);
            rows.add(List.of(
                    paidAt == null ? "" : STAMP.format(paidAt.atZone(zone)),
                    order.getId().toString().substring(0, 8),
                    order.getType().name(),
                    order.getBranch() == null ? "" : order.getBranch().getName(),
                    order.getSubtotal(), order.totalDiscount(), order.getServiceCharge(), order.getTaxTotal(),
                    order.getDeliveryFee(), order.getTipAmount(), order.getTotal(), paid, refunded,
                    orderPayments.stream().map(p -> p.getMethod().name()).distinct().collect(Collectors.joining("+"))));
        }
        String name = "sales_" + from + "_" + to;
        if (format == Format.CSV) {
            CsvWriter csv = new CsvWriter().row(List.of((Object[]) header));
            rows.forEach(csv::row);
            return new Report(name + ".csv", "text/csv; charset=utf-8", csv.toBytes());
        }
        PdfTableDocument pdf = new PdfTableDocument("Sales report", true)
                .subtitle("Period: " + from + " to " + to + (branch == null ? "" : "  |  Branch filter applied"))
                .subtitle("Generated " + STAMP.format(Instant.now().atZone(zone)))
                .summary("Orders paid", String.valueOf(rows.size()))
                .summary("Collected (GHS)", totalPaid.toPlainString())
                .summary("Refunded (GHS)", totalRefunded.toPlainString())
                .summary("Net (GHS)", totalPaid.subtract(totalRefunded).toPlainString())
                .summary("Tax collected (GHS)", totalTax.toPlainString())
                .columns(header, new double[]{1.4, 0.9, 0.9, 1.1, 0.8, 0.8, 0.7, 0.7, 0.7, 0.6, 0.8, 0.8, 0.8, 1.1});
        rows.forEach(pdf::row);
        return new Report(name + ".pdf", "application/pdf", pdf.toBytes());
    }

    // ------------------------------------------------------------ reconciliation

    @Transactional(readOnly = true)
    public Report reconciliation(LocalDate date, UUID branchId, UserPrincipal principal, Format format) {
        FinancialDtos.ReconciliationReport r = financialService.reconciliation(date, branchId, principal);
        List<List<Object>> lines = new ArrayList<>();
        lines.add(List.of("Gross sales", r.grossSales()));
        lines.add(List.of("Refunds", r.totalRefunds()));
        lines.add(List.of("Voids", r.totalVoids()));
        lines.add(List.of("Discounts", r.totalDiscounts()));
        lines.add(List.of("Net sales", r.netSales()));
        lines.add(List.of("Expected cash", r.expectedCash()));
        lines.add(List.of("Paid orders", r.paidOrders()));
        lines.add(List.of("Voided orders", r.voidedOrders()));
        lines.add(List.of("Refund count", r.refundCount()));
        lines.add(List.of("Closed shifts", r.shifts().closedShifts()));
        lines.add(List.of("Shift counted cash", r.shifts().countedCash()));
        lines.add(List.of("Shift variance", r.shifts().variance()));
        for (FinancialDtos.MethodTotal method : r.byMethod()) {
            lines.add(List.of("Collected via " + method.method().name(), method.amount() + " (" + method.count() + ")"));
        }
        if (r.signOff() != null) {
            lines.add(List.of("Signed off at", STAMP.format(r.signOff().signedAt().atZone(zone))));
            lines.add(List.of("Counted cash", r.signOff().countedCash() == null ? "" : r.signOff().countedCash()));
            lines.add(List.of("Cash variance", r.signOff().cashVariance() == null ? "" : r.signOff().cashVariance()));
        }
        String name = "reconciliation_" + date;
        if (format == Format.CSV) {
            CsvWriter csv = new CsvWriter().row(List.of("Metric", "Value"));
            lines.forEach(csv::row);
            return new Report(name + ".csv", "text/csv; charset=utf-8", csv.toBytes());
        }
        PdfTableDocument pdf = new PdfTableDocument("Daily reconciliation", false)
                .subtitle("Business date: " + date)
                .subtitle(r.signOff() == null ? "Not yet signed off" : "Signed off")
                .columns(new String[]{"Metric", "Value"}, new double[]{2, 1});
        lines.forEach(pdf::row);
        return new Report(name + ".pdf", "application/pdf", pdf.toBytes());
    }

    // ------------------------------------------------------------ audit

    @Transactional(readOnly = true)
    public Report audit(LocalDate from, LocalDate to, Format format) {
        validateRange(from, to);
        List<AuditLogEntity> logs = auditLogRepository.findAllBetween(
                from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        String[] header = {"Time", "Action", "Actor", "Entity", "Entity ID", "IP", "Details"};
        List<List<Object>> rows = logs.stream().map(log -> List.<Object>of(
                STAMP.format(log.getCreatedAt().atZone(zone)),
                log.getAction().name(),
                log.getActor() == null ? "" : log.getActor().getName(),
                log.getEntityType() == null ? "" : log.getEntityType(),
                log.getEntityId() == null ? "" : log.getEntityId().toString(),
                log.getIpAddress() == null ? "" : log.getIpAddress(),
                log.getMetadata() == null ? "" : log.getMetadata())).toList();
        String name = "audit_" + from + "_" + to;
        if (format == Format.CSV) {
            CsvWriter csv = new CsvWriter().row(List.of((Object[]) header));
            rows.forEach(csv::row);
            return new Report(name + ".csv", "text/csv; charset=utf-8", csv.toBytes());
        }
        PdfTableDocument pdf = new PdfTableDocument("Audit log", true)
                .subtitle("Period: " + from + " to " + to)
                .summary("Entries", String.valueOf(rows.size()))
                .columns(header, new double[]{1.1, 1.3, 1.1, 0.8, 2.0, 0.9, 3.0});
        rows.forEach(pdf::row);
        return new Report(name + ".pdf", "application/pdf", pdf.toBytes());
    }

    // ------------------------------------------------------------ timesheet

    @Transactional(readOnly = true)
    public Report timesheet(LocalDate from, LocalDate to, Format format) {
        validateRange(from, to);
        String[] header = {"Staff", "Role", "Worked (h)", "Scheduled (h)", "Overtime (h)", "Late arrivals", "Missed shifts", "Entries"};
        List<List<Object>> rows = workforceService.timesheet(from, to, null).stream().map(r -> List.<Object>of(
                r.name() == null ? "" : r.name(), r.role() == null ? "" : r.role(),
                hours(r.workedMinutes()), hours(r.scheduledMinutes()), hours(r.overtimeMinutes()),
                r.lateArrivals(), r.missedShifts(), r.entryCount())).toList();
        String name = "timesheet_" + from + "_" + to;
        if (format == Format.CSV) {
            CsvWriter csv = new CsvWriter().row(List.of((Object[]) header));
            rows.forEach(csv::row);
            return new Report(name + ".csv", "text/csv; charset=utf-8", csv.toBytes());
        }
        PdfTableDocument pdf = new PdfTableDocument("Timesheet", true)
                .subtitle("Period: " + from + " to " + to)
                .columns(header, new double[]{2, 1, 1, 1, 1, 1, 1, 0.8});
        rows.forEach(pdf::row);
        return new Report(name + ".pdf", "application/pdf", pdf.toBytes());
    }

    private static String hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(400, "'to' must not be before 'from'");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new ApiException(400, "Reports are limited to one year");
        }
    }

    private static UUID scopedBranch(UUID requested, UserPrincipal principal) {
        if (principal.role() == Role.MANAGER && principal.branchId() != null) {
            if (requested != null && !requested.equals(principal.branchId())) {
                throw new ApiException(403, "Forbidden");
            }
            return principal.branchId();
        }
        return requested;
    }
}
