package com.restaurantmanager.core.hr.payroll;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.reporting.CsvWriter;
import com.restaurantmanager.core.reporting.PdfTableDocument;
import com.restaurantmanager.core.reporting.ReportService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Payroll documents: payslip PDFs, each employee's payout details (bank or Mobile Money), and the payment files a
 * finalized run is paid with. The system records payments; it never moves money itself.
 */
@Service
public class PayrollDocumentService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final PayrollService payroll;
    private final PayrollRunRepository runRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;

    public PayrollDocumentService(PayrollService payroll, PayrollRunRepository runRepository, PayslipRepository payslipRepository,
                                  EmployeeRepository employeeRepository, HrAccessService accessService, HrAuditService audit) {
        this.payroll = payroll;
        this.runRepository = runRepository;
        this.payslipRepository = payslipRepository;
        this.employeeRepository = employeeRepository;
        this.accessService = accessService;
        this.audit = audit;
    }

    // ================================================================ payslip PDF

    /** A payslip as a one-page PDF. Same access as viewing it: payroll staff in scope, or the employee once released. */
    @Transactional
    public ReportService.Report payslipPdf(UserPrincipal principal, UUID payslipId) {
        PayrollService.PayslipDetail slip = payroll.payslip(principal, payslipId);
        PayrollService.PayslipSummary s = slip.summary();
        String currency = slip.currency();
        PdfTableDocument pdf = new PdfTableDocument("Payslip", false)
                .subtitle(s.employeeName() + "  |  " + s.employeeNumber())
                .subtitle("Pay period " + DATE.format(slip.periodStart()) + " to " + DATE.format(slip.periodEnd())
                        + (slip.payDate() == null ? "" : "  |  Pay date " + DATE.format(slip.payDate())))
                .subtitle("Payroll run " + slip.runNumber() + (slip.branchName() == null ? "" : "  |  " + slip.branchName()))
                .summary("Gross pay (" + currency + ")", money(s.gross()))
                .summary("Deductions (" + currency + ")", money(s.employeeDeductions()))
                .summary("Net pay (" + currency + ")", money(s.net()))
                .summary("Employer contributions (" + currency + ")", money(s.employerContributions()));
        if (s.paidAt() != null) {
            pdf.summary("Paid", humanize(s.paymentMethod()) + (s.paymentReference() == null ? "" : " (" + s.paymentReference() + ")"));
        }
        pdf.columns(new String[]{"Type", "Description", "Quantity", "Rate", "Amount (" + currency + ")"}, new double[]{1.1, 2.6, 0.9, 0.9, 1.1});
        for (PayrollService.LineView line : slip.lines()) {
            pdf.row(List.of(humanize(line.kind()), line.description() == null ? line.code() : line.description(),
                    line.quantity() == null ? "" : line.quantity().stripTrailingZeros().toPlainString(),
                    line.rate() == null ? "" : money(line.rate()), money(line.amount())));
        }
        audit.log(principal.userId(), AuditAction.HR_PAYSLIP_DOWNLOADED, "HR_PAYSLIP", payslipId,
                Map.of("run", slip.runNumber(), "employee", s.employeeNumber()));
        String name = "payslip-" + s.employeeNumber() + "-" + slip.periodStart() + ".pdf";
        return new ReportService.Report(name, "application/pdf", pdf.toBytes());
    }

    // ================================================================ payout details

    public record PayoutDetails(UUID employeeId, String employeeName, PaymentMethod method, String bankName, String bankBranch,
                                String bankAccountName, String bankAccountNumber, String momoNetwork, String momoNumber,
                                boolean masked, boolean complete, Instant updatedAt) {}

    public record PayoutRequest(PaymentMethod method,
                                @Size(max = 120) String bankName,
                                @Size(max = 120) String bankBranch,
                                @Size(max = 160) String bankAccountName,
                                @Pattern(regexp = "^[0-9A-Za-z -]{0,40}$", message = "Account number may only contain letters, digits, spaces and dashes") String bankAccountNumber,
                                @Size(max = 30) String momoNetwork,
                                @Pattern(regexp = "^\\+?[0-9 ()-]{0,20}$", message = "Enter a valid Mobile Money number") String momoNumber) {}

    @Transactional(readOnly = true)
    public PayoutDetails payout(UserPrincipal principal, UUID employeeId) {
        EmployeeEntity e = employee(employeeId);
        var access = accessService.access(principal);
        boolean own = isOwn(principal, e);
        boolean full = own || access.has(HrPermission.PAYROLL_PROCESS) || access.has(HrPermission.PAYROLL_PAY);
        boolean view = full || access.has(HrPermission.PAYROLL_VIEW) || access.has(HrPermission.PAYROLL_APPROVE)
                || access.has(HrPermission.PAYROLL_FINALIZE);
        if (!view || (!own && !access.canSeeBranch(e.primaryBranchId()))) {
            throw new ApiException(404, "Employee not found");
        }
        return view(e, !full);
    }

    @Transactional(readOnly = true)
    public PayoutDetails myPayout(UserPrincipal principal) {
        return view(me(principal), false);
    }

    @Transactional
    public PayoutDetails updatePayout(UserPrincipal principal, UUID employeeId, PayoutRequest request) {
        EmployeeEntity e = employee(employeeId);
        var access = accessService.access(principal);
        if (!isOwn(principal, e)) {
            if (!access.has(HrPermission.PAYROLL_PROCESS) && !access.has(HrPermission.PAYROLL_PAY)) {
                throw new ApiException(403, "You cannot change payout details");
            }
            access.assertBranch(e.primaryBranchId());
        }
        return applyPayout(principal, e, request, false);
    }

    @Transactional
    public PayoutDetails updateMyPayout(UserPrincipal principal, PayoutRequest request) {
        return applyPayout(principal, me(principal), request, true);
    }

    private PayoutDetails applyPayout(UserPrincipal principal, EmployeeEntity e, PayoutRequest r, boolean selfService) {
        if (r == null || r.method() == null) {
            throw new ApiException(400, "Choose how this person is paid");
        }
        if (r.method() == PaymentMethod.OTHER) {
            throw new ApiException(400, "Choose bank, Mobile Money or cash");
        }
        if (r.method() == PaymentMethod.BANK && (blank(r.bankName()) || blank(r.bankAccountName()) || blank(r.bankAccountNumber()))) {
            throw new ApiException(400, "Bank payments need the bank, account name and account number");
        }
        if (r.method() == PaymentMethod.MOBILE_MONEY && (blank(r.momoNetwork()) || blank(r.momoNumber()))) {
            throw new ApiException(400, "Mobile Money payments need the network and number");
        }
        e.setPayoutMethod(r.method());
        e.setBankName(trim(r.bankName()));
        e.setBankBranch(trim(r.bankBranch()));
        e.setBankAccountName(trim(r.bankAccountName()));
        e.setBankAccountNumber(trim(r.bankAccountNumber()));
        e.setMomoNetwork(trim(r.momoNetwork()));
        e.setMomoNumber(r.momoNumber() == null || r.momoNumber().isBlank() ? null : digits(r.momoNumber()));
        e.setPayoutUpdatedAt(Instant.now());
        employeeRepository.save(e);
        // Never the numbers themselves in the audit trail.
        audit.log(principal.userId(), AuditAction.HR_PAYOUT_DETAILS_CHANGED, "HR_EMPLOYEE", e.getId(),
                Map.of("method", r.method().name(), "selfService", selfService));
        return view(e, false);
    }

    // ================================================================ payment files

    public record PaymentFileGroup(PaymentMethod method, int people, BigDecimal total) {}

    public record MissingPayout(UUID employeeId, String employeeName, String employeeNumber, BigDecimal net, String problem) {}

    public record PaymentFileSummary(UUID runId, String runNumber, PayrollRunStatus status, String currency, boolean ready,
                                     List<PaymentFileGroup> groups, List<MissingPayout> missing) {}

    /** What a run's payment files would contain: people and totals per method, and who cannot be paid yet. */
    @Transactional(readOnly = true)
    public PaymentFileSummary paymentFileSummary(UserPrincipal principal, UUID runId) {
        PayrollRunEntity run = runForPayment(principal, runId);
        Map<PaymentMethod, List<Row>> byMethod = new LinkedHashMap<>();
        List<MissingPayout> missing = new ArrayList<>();
        for (Row row : unpaidRows(run)) {
            String problem = problem(row.employee());
            if (problem != null) {
                missing.add(new MissingPayout(row.employee().getId(), row.employee().fullName(), row.employee().getEmployeeNumber(),
                        row.slip().getNet(), problem));
            } else {
                byMethod.computeIfAbsent(row.employee().getPayoutMethod(), ignored -> new ArrayList<>()).add(row);
            }
        }
        List<PaymentFileGroup> groups = byMethod.entrySet().stream()
                .map(en -> new PaymentFileGroup(en.getKey(), en.getValue().size(),
                        en.getValue().stream().map(r -> r.slip().getNet()).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
        boolean ready = run.getStatus() == PayrollRunStatus.FINALIZED || run.getStatus() == PayrollRunStatus.PAID;
        return new PaymentFileSummary(run.getId(), run.getRunNumber(), run.getStatus(), run.getCurrency(), ready, groups, missing);
    }

    /**
     * The CSV to upload to the bank or Mobile Money bulk-payment service: the run's unpaid payslips whose employees
     * are paid that way. Refused while anyone due that way is missing details, so a file is never silently short.
     */
    @Transactional
    public ReportService.Report paymentFile(UserPrincipal principal, UUID runId, PaymentMethod method) {
        if (method != PaymentMethod.BANK && method != PaymentMethod.MOBILE_MONEY) {
            throw new ApiException(400, "Payment files are for bank or Mobile Money payments");
        }
        PayrollRunEntity run = runForPayment(principal, runId);
        if (run.getStatus() != PayrollRunStatus.FINALIZED && run.getStatus() != PayrollRunStatus.PAID) {
            throw new ApiException(409, "Finalize the payroll run before exporting payment files");
        }
        List<Row> rows = unpaidRows(run).stream().filter(r -> r.employee().getPayoutMethod() == method).toList();
        List<String> incomplete = rows.stream().filter(r -> problem(r.employee()) != null).map(r -> r.employee().fullName()).toList();
        if (!incomplete.isEmpty()) {
            throw new ApiException(409, "Missing " + humanize(method) + " details for: " + String.join(", ", incomplete));
        }
        if (rows.isEmpty()) {
            throw new ApiException(409, "Nobody in this run is due a " + humanize(method) + " payment");
        }
        CsvWriter csv = new CsvWriter();
        BigDecimal total = BigDecimal.ZERO;
        if (method == PaymentMethod.BANK) {
            csv.row(List.of("Employee no.", "Name", "Bank", "Branch", "Account name", "Account number", "Amount", "Currency", "Reference"));
            for (Row r : rows) {
                EmployeeEntity e = r.employee();
                csv.row(List.of(e.getEmployeeNumber(), e.fullName(), e.getBankName(), nz(e.getBankBranch()), e.getBankAccountName(),
                        e.getBankAccountNumber(), r.slip().getNet(), run.getCurrency(), reference(run, e)));
                total = total.add(r.slip().getNet());
            }
        } else {
            csv.row(List.of("Employee no.", "Name", "Network", "Mobile Money number", "Amount", "Currency", "Reference"));
            for (Row r : rows) {
                EmployeeEntity e = r.employee();
                csv.row(List.of(e.getEmployeeNumber(), e.fullName(), e.getMomoNetwork(), e.getMomoNumber(), r.slip().getNet(),
                        run.getCurrency(), reference(run, e)));
                total = total.add(r.slip().getNet());
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("run", run.getRunNumber());
        meta.put("method", method.name());
        meta.put("people", rows.size());
        meta.put("total", total);
        audit.log(principal.userId(), AuditAction.HR_PAYMENT_FILE_EXPORTED, "HR_PAYROLL_RUN", run.getId(), meta);
        String name = run.getRunNumber() + "-" + (method == PaymentMethod.BANK ? "bank" : "momo") + "-payments.csv";
        return new ReportService.Report(name, "text/csv; charset=utf-8", csv.toBytes());
    }

    // ================================================================ helpers

    private record Row(PayslipEntity slip, EmployeeEntity employee) {}

    /** Unpaid payslips with something to pay, in employee-number order. */
    private List<Row> unpaidRows(PayrollRunEntity run) {
        List<PayslipEntity> slips = payslipRepository.findByRunId(run.getId()).stream()
                .filter(p -> p.getPaidAt() == null && p.getNet() != null && p.getNet().signum() > 0)
                .toList();
        Map<UUID, EmployeeEntity> employees = new LinkedHashMap<>();
        employeeRepository.findAllById(slips.stream().map(PayslipEntity::getEmployeeId).toList()).forEach(e -> employees.put(e.getId(), e));
        return slips.stream()
                .filter(p -> employees.containsKey(p.getEmployeeId()))
                .map(p -> new Row(p, employees.get(p.getEmployeeId())))
                .sorted(Comparator.comparing(r -> r.employee().getEmployeeNumber()))
                .toList();
    }

    private PayrollRunEntity runForPayment(UserPrincipal principal, UUID runId) {
        var access = accessService.access(principal);
        if (!access.has(HrPermission.PAYROLL_PAY)) {
            throw new ApiException(403, "You cannot export payroll payments");
        }
        PayrollRunEntity run = runRepository.findById(runId).orElseThrow(() -> new ApiException(404, "Payroll run not found"));
        if (!access.canSeeBranch(run.getBranchId())) {
            throw new ApiException(404, "Payroll run not found");
        }
        return run;
    }

    static String problem(EmployeeEntity e) {
        PaymentMethod m = e.getPayoutMethod();
        if (m == null) return "No payout method set";
        if (m == PaymentMethod.BANK && (blank(e.getBankName()) || blank(e.getBankAccountName()) || blank(e.getBankAccountNumber()))) {
            return "Bank details incomplete";
        }
        if (m == PaymentMethod.MOBILE_MONEY && (blank(e.getMomoNetwork()) || blank(e.getMomoNumber()))) {
            return "Mobile Money details incomplete";
        }
        return null;
    }

    private PayoutDetails view(EmployeeEntity e, boolean mask) {
        return new PayoutDetails(e.getId(), e.fullName(), e.getPayoutMethod(), e.getBankName(), e.getBankBranch(), e.getBankAccountName(),
                mask ? mask(e.getBankAccountNumber()) : e.getBankAccountNumber(), e.getMomoNetwork(),
                mask ? mask(e.getMomoNumber()) : e.getMomoNumber(), mask, problem(e) == null, e.getPayoutUpdatedAt());
    }

    static String mask(String value) {
        if (value == null || value.isBlank()) return value;
        String v = value.replaceAll("\\s", "");
        return v.length() <= 4 ? "****" : "*".repeat(Math.min(8, v.length() - 4)) + v.substring(v.length() - 4);
    }

    private boolean isOwn(UserPrincipal principal, EmployeeEntity e) {
        return e.getUserId() != null && e.getUserId().equals(principal.userId());
    }

    private EmployeeEntity employee(UUID id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ApiException(404, "Employee not found"));
    }

    private EmployeeEntity me(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
    }

    private static String reference(PayrollRunEntity run, EmployeeEntity e) {
        return run.getRunNumber() + "-" + e.getEmployeeNumber();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String humanize(Enum<?> value) {
        if (value == null) return "";
        if (value == PaymentMethod.MOBILE_MONEY) return "Mobile Money";
        String s = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return blank(value) ? null : value.trim();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String digits(String phone) {
        String t = phone.trim();
        String d = t.replaceAll("[^0-9]", "");
        return t.startsWith("+") ? "+" + d : d;
    }
}
