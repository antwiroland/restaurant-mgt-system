package com.restaurantmanager.core.hr.payroll;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.branch.AssignmentType;
import com.restaurantmanager.core.hr.branch.BranchAssignmentEntity;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetRepository;
import com.restaurantmanager.core.reporting.CsvWriter;
import com.restaurantmanager.core.reporting.ReportService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Around the payroll run: loans and advances repaid from every run, SSNIT and PAYE returns, and the labour cost of a
 * run split across the branches people actually worked at.
 */
@Service
public class PayrollExtrasService {
    private static final HrPermission[] ANY_PAYROLL = {HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE,
            HrPermission.PAYROLL_FINALIZE, HrPermission.PAYROLL_PAY};

    private final RecurringDeductionRepository recurringRepository;
    private final RecurringInstallmentRepository installmentRepository;
    private final PayrollRunRepository runRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository lineRepository;
    private final PayrollRuleService ruleService;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final BranchAssignmentRepository assignmentRepository;
    private final TimesheetRepository timesheetRepository;
    private final TimesheetLineRepository timesheetLineRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;

    public PayrollExtrasService(RecurringDeductionRepository recurringRepository, RecurringInstallmentRepository installmentRepository,
                                PayrollRunRepository runRepository, PayslipRepository payslipRepository, PayslipLineRepository lineRepository,
                                PayrollRuleService ruleService, EmployeeRepository employeeRepository, BranchRepository branchRepository,
                                BranchAssignmentRepository assignmentRepository, TimesheetRepository timesheetRepository,
                                TimesheetLineRepository timesheetLineRepository, HrAccessService accessService, HrAuditService audit) {
        this.recurringRepository = recurringRepository;
        this.installmentRepository = installmentRepository;
        this.runRepository = runRepository;
        this.payslipRepository = payslipRepository;
        this.lineRepository = lineRepository;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.assignmentRepository = assignmentRepository;
        this.timesheetRepository = timesheetRepository;
        this.timesheetLineRepository = timesheetLineRepository;
        this.accessService = accessService;
        this.audit = audit;
    }

    // ================================================================ loans and advances

    public record RecurringRequest(@NotNull @Pattern(regexp = "LOAN|ADVANCE|OTHER") String kind,
                                   @NotBlank @Size(max = 120) String description,
                                   @NotNull @DecimalMin(value = "0.01") BigDecimal installmentAmount,
                                   @DecimalMin(value = "0.01") BigDecimal totalAmount,
                                   @NotNull LocalDate startDate) {}

    public record StopRequest(@NotBlank @Size(max = 300) String reason) {}

    public record RecurringView(UUID id, UUID employeeId, String kind, String description, BigDecimal installmentAmount, BigDecimal totalAmount,
                                BigDecimal repaid, BigDecimal remaining, LocalDate startDate, boolean active, boolean finished,
                                String stoppedReason, Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<RecurringView> listRecurring(UserPrincipal principal, UUID employeeId) {
        HrAccess access = accessService.requireAny(principal, ANY_PAYROLL);
        employeeInScope(employeeId, access);
        return recurringRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream().map(this::view).toList();
    }

    @Transactional
    public RecurringView addRecurring(UserPrincipal principal, UUID employeeId, RecurringRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        EmployeeEntity employee = employeeInScope(employeeId, access);
        if (employee.getEmploymentStatus().isExit()) {
            throw new ApiException(409, "This person no longer works here");
        }
        if (r.totalAmount() != null && r.installmentAmount().compareTo(r.totalAmount()) > 0) {
            throw new ApiException(400, "The instalment cannot be more than the total");
        }
        RecurringDeductionEntity d = new RecurringDeductionEntity();
        d.setEmployeeId(employeeId);
        d.setKind(r.kind());
        d.setDescription(r.description().trim());
        d.setInstallmentAmount(r.installmentAmount().setScale(2, RoundingMode.HALF_UP));
        d.setTotalAmount(r.totalAmount() == null ? null : r.totalAmount().setScale(2, RoundingMode.HALF_UP));
        d.setStartDate(r.startDate());
        d.setCreatedBy(access.userId());
        recurringRepository.save(d);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", "recurring-deduction-added");
        meta.put("kind", d.getKind());
        meta.put("installment", d.getInstallmentAmount());
        meta.put("total", d.getTotalAmount() == null ? "none" : d.getTotalAmount());
        audit.log(access.userId(), AuditAction.HR_PAYROLL_CHANGED, "Employee", employeeId, meta);
        return view(d);
    }

    /** Stops future instalments; what was already taken stays on the payslips. */
    @Transactional
    public RecurringView stopRecurring(UserPrincipal principal, UUID id, StopRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        RecurringDeductionEntity d = recurringRepository.findById(id).orElseThrow(() -> new ApiException(404, "Deduction not found"));
        employeeInScope(d.getEmployeeId(), access);
        if (!d.isActive()) {
            throw new ApiException(409, "This deduction is already stopped");
        }
        d.setActive(false);
        d.setStoppedReason(r.reason().trim());
        recurringRepository.save(d);
        audit.log(access.userId(), AuditAction.HR_PAYROLL_CHANGED, "Employee", d.getEmployeeId(),
                Map.of("operation", "recurring-deduction-stopped", "reason", d.getStoppedReason()));
        return view(d);
    }

    private RecurringView view(RecurringDeductionEntity d) {
        BigDecimal repaid = installmentRepository.repaid(d.getId());
        BigDecimal remaining = d.getTotalAmount() == null ? null : d.getTotalAmount().subtract(repaid).max(BigDecimal.ZERO);
        return new RecurringView(d.getId(), d.getEmployeeId(), d.getKind(), d.getDescription(), d.getInstallmentAmount(), d.getTotalAmount(),
                repaid, remaining, d.getStartDate(), d.isActive(), remaining != null && remaining.signum() == 0, d.getStoppedReason(), d.getCreatedAt());
    }

    private EmployeeEntity employeeInScope(UUID employeeId, HrAccess access) {
        EmployeeEntity e = employeeRepository.findById(employeeId).orElseThrow(() -> new ApiException(404, "Employee not found"));
        if (!access.canSeeBranch(e.primaryBranchId())) {
            throw new ApiException(404, "Employee not found");
        }
        return e;
    }

    // ================================================================ returns

    /**
     * The monthly returns for a calculated run: SSNIT (contributions on basic pay per member) and PAYE (taxable pay and
     * tax per employee). Built from the rules filed as SSNIT and PAYE.
     */
    @Transactional(readOnly = true)
    public ReportService.Report statutoryReturn(UserPrincipal principal, UUID runId, String type) {
        HrAccess access = accessService.requireAny(principal, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_FINALIZE);
        PayrollRunEntity run = runInScope(runId, access);
        if (run.getStatus() == PayrollRunStatus.DRAFT || run.getStatus() == PayrollRunStatus.CANCELLED) {
            throw new ApiException(409, "Calculate the run first");
        }
        boolean ssnit = "ssnit".equalsIgnoreCase(type);
        if (!ssnit && !"paye".equalsIgnoreCase(type)) {
            throw new ApiException(400, "Choose the SSNIT or PAYE return");
        }
        Map<String, String> codes = ruleService.reportCodes();
        List<PayslipEntity> slips = payslipRepository.findByRunId(run.getId());
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(slips.stream().map(PayslipEntity::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        CsvWriter csv = new CsvWriter();
        String period = run.getPeriodStart() + " to " + run.getPeriodEnd();
        BigDecimal t1 = BigDecimal.ZERO, t2 = BigDecimal.ZERO, t3 = BigDecimal.ZERO;
        int people = 0;
        if (ssnit) {
            csv.row(List.of("Period", "SSNIT number", "Ghana Card", "Employee no.", "Name", "Basic salary", "Employee (SSNIT)", "Employer (SSNIT)", "Total"));
        } else {
            csv.row(List.of("Period", "TIN / Ghana Card", "Employee no.", "Name", "Gross pay", "Relief (before-tax deductions)", "Taxable pay", "PAYE"));
        }
        for (PayslipEntity slip : slips.stream().sorted(Comparator.comparing(s -> name(employees.get(s.getEmployeeId())))).toList()) {
            EmployeeEntity e = employees.get(slip.getEmployeeId());
            BigDecimal employee = BigDecimal.ZERO, employer = BigDecimal.ZERO, paye = BigDecimal.ZERO;
            for (PayslipLineEntity l : lineRepository.findByPayslipIdOrderBySortOrderAsc(slip.getId())) {
                String as = codes.get(l.getCode());
                if ("SSNIT_EMPLOYEE".equals(as)) employee = employee.add(l.getAmount());
                if ("SSNIT_EMPLOYER".equals(as)) employer = employer.add(l.getAmount());
                if ("PAYE".equals(as)) paye = paye.add(l.getAmount());
            }
            if (ssnit) {
                if (employee.signum() == 0 && employer.signum() == 0) continue;
                csv.row(List.of(period, nz(slip.getSsnitNumber()), e == null ? "" : nz(e.getNationalId()), e == null ? "" : e.getEmployeeNumber(),
                        name(e), slip.getBasic(), employee, employer, employee.add(employer)));
                t1 = t1.add(slip.getBasic());
                t2 = t2.add(employee);
                t3 = t3.add(employer);
            } else {
                BigDecimal relief = slip.getGross().subtract(slip.getTaxable()).max(BigDecimal.ZERO);
                csv.row(List.of(period, nz(slip.getTin()), e == null ? "" : e.getEmployeeNumber(), name(e), slip.getGross(), relief,
                        slip.getTaxable(), paye));
                t1 = t1.add(slip.getGross());
                t2 = t2.add(slip.getTaxable());
                t3 = t3.add(paye);
            }
            people++;
        }
        if (ssnit) {
            csv.row(List.of("Total", "", "", "", people + " members", t1, t2, t3, t2.add(t3)));
        } else {
            csv.row(List.of("Total", "", "", people + " employees", t1, "", t2, t3));
        }
        audit.log(access.userId(), AuditAction.HR_PAYROLL_CHANGED, "PayrollRun", run.getId(),
                Map.of("operation", ssnit ? "ssnit-return-exported" : "paye-return-exported", "people", people));
        return new ReportService.Report(run.getRunNumber() + "-" + (ssnit ? "ssnit" : "paye") + "-return.csv", "text/csv; charset=utf-8", csv.toBytes());
    }

    // ================================================================ cost by branch

    public record BranchCost(UUID branchId, String branchName, int people, BigDecimal gross, BigDecimal employer, BigDecimal total) {}

    public record SplitEmployee(UUID employeeId, String employeeName, Map<String, BigDecimal> shareByBranch) {}

    public record CostAllocation(UUID runId, List<BranchCost> branches, List<SplitEmployee> splitEmployees) {}

    /**
     * Who the run's cost belongs to: each payslip's gross pay and employer contributions are split by the paid minutes
     * worked at each branch (locked timesheets in the run); without them, by days based at each branch (transfers);
     * otherwise all to the run's branch.
     */
    @Transactional(readOnly = true)
    public CostAllocation costAllocation(UserPrincipal principal, UUID runId) {
        HrAccess access = accessService.requireAny(principal, ANY_PAYROLL);
        PayrollRunEntity run = runInScope(runId, access);
        Map<UUID, String> names = new HashMap<>();
        branchRepository.findAll().forEach(b -> names.put(b.getId(), b.getName()));
        Map<UUID, List<TimesheetEntity>> sheets = timesheetRepository.findByPayrollRunId(run.getId()).stream()
                .collect(Collectors.groupingBy(TimesheetEntity::getEmployeeId));
        List<PayslipEntity> slips = payslipRepository.findByRunId(run.getId());
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(slips.stream().map(PayslipEntity::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));

        Map<UUID, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<UUID, java.util.Set<UUID>> peopleAt = new HashMap<>();
        List<SplitEmployee> split = new ArrayList<>();
        for (PayslipEntity slip : slips) {
            Map<UUID, Long> weights = new LinkedHashMap<>();
            for (TimesheetEntity t : sheets.getOrDefault(slip.getEmployeeId(), List.of())) {
                for (TimesheetLineEntity l : timesheetLineRepository.findByTimesheetIdOrderByWorkDateAscSortOrderAsc(t.getId())) {
                    if (l.getPaidMinutes() > 0) {
                        weights.merge(l.getBranchId() == null ? run.getBranchId() : l.getBranchId(), (long) l.getPaidMinutes(), Long::sum);
                    }
                }
            }
            if (weights.isEmpty()) {
                for (BranchAssignmentEntity a : assignmentRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(slip.getEmployeeId())) {
                    if (a.getAssignmentType() != AssignmentType.PRIMARY || a.getBranchId() == null) continue;
                    LocalDate from = a.getStartDate().isAfter(run.getPeriodStart()) ? a.getStartDate() : run.getPeriodStart();
                    LocalDate to = a.getEndDate() == null || a.getEndDate().isAfter(run.getPeriodEnd()) ? run.getPeriodEnd() : a.getEndDate();
                    if (!to.isBefore(from)) weights.merge(a.getBranchId(), ChronoUnit.DAYS.between(from, to) + 1, Long::sum);
                }
            }
            if (weights.isEmpty()) {
                weights.put(run.getBranchId(), 1L);
            }
            long sum = weights.values().stream().mapToLong(Long::longValue).sum();
            Map<UUID, BigDecimal> grossShare = shares(slip.getGross(), weights, sum);
            Map<UUID, BigDecimal> employerShare = shares(slip.getEmployerContributions(), weights, sum);
            for (UUID branch : weights.keySet()) {
                BigDecimal[] t = totals.computeIfAbsent(branch, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                t[0] = t[0].add(grossShare.get(branch));
                t[1] = t[1].add(employerShare.get(branch));
                peopleAt.computeIfAbsent(branch, k -> new java.util.HashSet<>()).add(slip.getEmployeeId());
            }
            if (weights.size() > 1) {
                Map<String, BigDecimal> pct = new LinkedHashMap<>();
                weights.forEach((b, w) -> pct.put(names.getOrDefault(b, "Unknown branch"),
                        BigDecimal.valueOf(w * 100).divide(BigDecimal.valueOf(sum), 1, RoundingMode.HALF_UP)));
                split.add(new SplitEmployee(slip.getEmployeeId(), name(employees.get(slip.getEmployeeId())), pct));
            }
        }
        List<BranchCost> branches = totals.entrySet().stream()
                .map(en -> new BranchCost(en.getKey(), names.get(en.getKey()), peopleAt.get(en.getKey()).size(), en.getValue()[0], en.getValue()[1],
                        en.getValue()[0].add(en.getValue()[1])))
                .sorted(Comparator.comparing(BranchCost::total).reversed())
                .toList();
        return new CostAllocation(run.getId(), branches, split);
    }

    /** Splits {@code amount} by weight to the cent; the rounding difference goes to the largest share. */
    static Map<UUID, BigDecimal> shares(BigDecimal amount, Map<UUID, Long> weights, long sum) {
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        BigDecimal given = BigDecimal.ZERO;
        UUID largest = null;
        for (Map.Entry<UUID, Long> w : weights.entrySet()) {
            BigDecimal share = amount.multiply(BigDecimal.valueOf(w.getValue())).divide(BigDecimal.valueOf(sum), 2, RoundingMode.HALF_UP);
            out.put(w.getKey(), share);
            given = given.add(share);
            if (largest == null || w.getValue() > weights.get(largest)) largest = w.getKey();
        }
        out.put(largest, out.get(largest).add(amount.subtract(given)));
        return out;
    }

    private PayrollRunEntity runInScope(UUID runId, HrAccess access) {
        PayrollRunEntity run = runRepository.findById(runId).orElseThrow(() -> new ApiException(404, "Payroll run not found"));
        if (!access.canSeeBranch(run.getBranchId())) {
            throw new ApiException(404, "Payroll run not found");
        }
        return run;
    }

    private static String name(EmployeeEntity e) {
        return e == null ? "" : e.fullName();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
