package com.restaurantmanager.core.hr.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.common.HrSequenceService;
import com.restaurantmanager.core.hr.contract.ContractEntity;
import com.restaurantmanager.core.hr.contract.ContractRepository;
import com.restaurantmanager.core.hr.contract.ContractStatus;
import com.restaurantmanager.core.hr.contract.SalaryType;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.hr.leave.LeaveCalculator;
import com.restaurantmanager.core.hr.leave.LeaveRequestEntity;
import com.restaurantmanager.core.hr.leave.LeaveRequestRepository;
import com.restaurantmanager.core.hr.leave.LeaveSetupService;
import com.restaurantmanager.core.hr.leave.LeaveStatus;
import com.restaurantmanager.core.hr.leave.LeaveTypeEntity;
import com.restaurantmanager.core.hr.leave.LeaveTypeRepository;
import com.restaurantmanager.core.hr.timesheet.OvertimeSettingsService;
import com.restaurantmanager.core.hr.timesheet.TimesheetCalculator;
import com.restaurantmanager.core.hr.timesheet.TimesheetEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetStatus;
import com.restaurantmanager.core.hr.tips.TipService;
import com.restaurantmanager.core.hr.tips.TipSource;
import com.restaurantmanager.core.notification.push.PushNotificationService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payroll runs: one branch and period, Draft → Calculated → Approved → Finalized → Paid. Calculation snapshots every
 * payslip (and reserves the locked timesheets it used); approval needs a second person; finalized payslips never change.
 */
@Service
public class PayrollService {
    static final int MAX_PERIOD_DAYS = 31;
    /** Issue codes that block approval. */
    static final Set<String> ERRORS = Set.of("UNLOCKED_TIMESHEETS", "NEGATIVE_NET");
    private static final Set<ContractStatus> PAYABLE_CONTRACTS = EnumSet.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED,
            ContractStatus.ENDED, ContractStatus.RENEWED);
    private static final HrPermission[] ANY_PAYROLL = {HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE,
            HrPermission.PAYROLL_FINALIZE, HrPermission.PAYROLL_PAY};

    private final PayrollRunRepository runRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository lineRepository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayrollRuleService ruleService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final ContractRepository contractRepository;
    private final TimesheetRepository timesheetRepository;
    private final TimesheetLineRepository timesheetLineRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveSetupService leaveSetup;
    private final OvertimeSettingsService overtimeSettings;
    private final HrSequenceService sequences;
    private final BranchRepository branchRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final PushNotificationService push;
    private final ObjectMapper objectMapper;
    private final TipService tipService;

    public PayrollService(PayrollRunRepository runRepository, PayslipRepository payslipRepository, PayslipLineRepository lineRepository,
                          PayrollAdjustmentRepository adjustmentRepository, PayrollRuleService ruleService, EmployeeRepository employeeRepository,
                          EmployeeService employeeService, ContractRepository contractRepository, TimesheetRepository timesheetRepository,
                          TimesheetLineRepository timesheetLineRepository, LeaveRequestRepository leaveRequestRepository,
                          LeaveTypeRepository leaveTypeRepository, LeaveSetupService leaveSetup, OvertimeSettingsService overtimeSettings,
                          HrSequenceService sequences, BranchRepository branchRepository, HrAccessService accessService, HrAuditService audit,
                          PushNotificationService push, ObjectMapper objectMapper, TipService tipService) {
        this.runRepository = runRepository;
        this.payslipRepository = payslipRepository;
        this.lineRepository = lineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.contractRepository = contractRepository;
        this.timesheetRepository = timesheetRepository;
        this.timesheetLineRepository = timesheetLineRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveSetup = leaveSetup;
        this.overtimeSettings = overtimeSettings;
        this.sequences = sequences;
        this.branchRepository = branchRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.push = push;
        this.objectMapper = objectMapper;
        this.tipService = tipService;
    }

    // ================================================================ runs

    @Transactional
    public RunDetail create(UserPrincipal principal, CreateRunRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        access.assertBranch(r.branchId());
        BranchEntity branch = branchRepository.findById(r.branchId()).orElseThrow(() -> new ApiException(404, "Branch not found"));
        if (r.periodEnd().isBefore(r.periodStart()) || ChronoUnit.DAYS.between(r.periodStart(), r.periodEnd()) >= MAX_PERIOD_DAYS) {
            throw new ApiException(400, "A payroll period is 1 to " + MAX_PERIOD_DAYS + " days");
        }
        if (r.payDate() != null && r.payDate().isBefore(r.periodStart())) {
            throw new ApiException(400, "The pay date cannot be before the period starts");
        }
        if (!runRepository.findOverlapping(branch.getId(), r.periodStart(), r.periodEnd(), PayrollRunStatus.CANCELLED).isEmpty()) {
            throw new ApiException(409, "Another payroll run at this branch covers part of this period");
        }
        PayrollRunEntity run = new PayrollRunEntity();
        run.setRunNumber(sequences.next("payroll_run", "PAY"));
        run.setBranchId(branch.getId());
        run.setPeriodStart(r.periodStart());
        run.setPeriodEnd(r.periodEnd());
        run.setPayDate(r.payDate());
        run.setNotes(blank(r.notes()));
        run.setCreatedBy(access.userId());
        runRepository.save(run);
        log(access, run, "created", null);
        return detail(run, access);
    }

    @Transactional(readOnly = true)
    public List<RunSummary> list(UserPrincipal principal, UUID branchId) {
        HrAccess access = accessService.requireAny(principal, ANY_PAYROLL);
        List<PayrollRunEntity> rows;
        if (branchId != null) {
            access.assertBranch(branchId);
            rows = runRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(List.of(branchId));
        } else if (access.allBranches()) {
            rows = runRepository.findAllByOrderByPeriodStartDescCreatedAtDesc();
        } else if (access.branchIds().isEmpty()) {
            rows = List.of();
        } else {
            rows = runRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(access.branchIds());
        }
        Map<UUID, String> branches = branchNames();
        return rows.stream().map(r -> summary(r, branches, null)).toList();
    }

    @Transactional(readOnly = true)
    public RunDetail get(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.requireAny(principal, ANY_PAYROLL);
        return detail(load(id, access), access);
    }

    // ================================================================ calculation

    @Transactional
    public RunDetail calculate(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        PayrollRunEntity run = load(id, access);
        if (run.getStatus() != PayrollRunStatus.DRAFT && run.getStatus() != PayrollRunStatus.CALCULATED) {
            throw new ApiException(409, "Only draft or calculated runs can be (re)calculated");
        }
        lineRepository.deleteByRunId(run.getId());
        payslipRepository.deleteByRunId(run.getId());
        timesheetRepository.releaseRun(run.getId());
        timesheetRepository.flush();
        tipService.release(run.getId());

        LocalDate start = run.getPeriodStart();
        LocalDate end = run.getPeriodEnd();
        Map<UUID, List<PayrollAdjustmentEntity>> adjustments = adjustmentRepository.findByRunIdOrderByCreatedAtAsc(run.getId()).stream()
                .collect(Collectors.groupingBy(PayrollAdjustmentEntity::getEmployeeId));
        Map<UUID, LeaveTypeEntity> leaveTypes = leaveTypeRepository.findAll().stream().collect(Collectors.toMap(LeaveTypeEntity::getId, Function.identity()));
        Function<LocalDate, TimesheetCalculator.Rule> overtimeRules = overtimeSettings.ruleResolver(run.getBranchId());
        List<Skipped> skipped = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO, net = BigDecimal.ZERO, employer = BigDecimal.ZERO;
        int count = 0;

        List<EmployeeEntity> employees = employeeRepository.findByPrimaryBranchId(run.getBranchId()).stream()
                .filter(e -> e.getHireDate() == null || !e.getHireDate().isAfter(end))
                .filter(e -> e.getExitDate() == null || !e.getExitDate().isBefore(start))
                .sorted(Comparator.comparing(EmployeeEntity::fullName))
                .toList();
        for (EmployeeEntity e : employees) {
            if (!payslipRepository.findInOtherRuns(e.getId(), run.getId(), start, end, PayrollRunStatus.CANCELLED).isEmpty()) {
                skipped.add(new Skipped(e.getId(), e.fullName(), "Already paid in another run covering this period"));
                continue;
            }
            ContractEntity contract = contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(e.getId()).stream()
                    .filter(c -> PAYABLE_CONTRACTS.contains(c.getStatus()))
                    .filter(c -> !c.getStartDate().isAfter(end) && (c.getEndDate() == null || !c.getEndDate().isBefore(start)))
                    .findFirst().orElse(null);
            if (contract == null) {
                skipped.add(new Skipped(e.getId(), e.fullName(), "No contract covers this period"));
                continue;
            }
            LocalDate from = latest(start, e.getHireDate(), contract.getStartDate());
            LocalDate to = earliest(end, e.getExitDate(), contract.getEndDate());

            List<String> issues = new ArrayList<>();
            List<TimesheetEntity> overlapping = timesheetRepository.findOverlapping(e.getId(), start, end);
            List<TimesheetEntity> inside = overlapping.stream()
                    .filter(t -> !t.getPeriodStart().isBefore(start) && !t.getPeriodEnd().isAfter(end)).toList();
            if (inside.size() < overlapping.size()) issues.add("TIMESHEET_OUTSIDE_PERIOD");
            List<TimesheetEntity> usable = inside.stream()
                    .filter(t -> t.getStatus() == TimesheetStatus.LOCKED && (t.getPayrollRunId() == null || t.getPayrollRunId().equals(run.getId())))
                    .toList();
            boolean hourlyPaid = contract.getSalaryType() == SalaryType.HOURLY || contract.getSalaryType() == SalaryType.DAILY;
            if (inside.stream().anyMatch(t -> t.getStatus() != TimesheetStatus.LOCKED)) {
                issues.add(hourlyPaid ? "UNLOCKED_TIMESHEETS" : "UNLOCKED_OVERTIME");
            }
            List<PayrollAdjustmentEntity> mine = adjustments.getOrDefault(e.getId(), List.of());
            List<TipService.PayrollShare> shares = tipService.sharesFor(e.getId(), run.getId(), start, end);
            if (hourlyPaid && usable.isEmpty() && mine.isEmpty() && shares.isEmpty() && !issues.contains("UNLOCKED_TIMESHEETS")) {
                skipped.add(new Skipped(e.getId(), e.fullName(), "No locked timesheet hours in this period"));
                continue;
            }

            int paid = usable.stream().mapToInt(TimesheetEntity::getPaidMinutes).sum();
            int overtime = usable.stream().mapToInt(TimesheetEntity::payableOvertimeMinutes).sum();
            int holiday = usable.stream().mapToInt(TimesheetEntity::getHolidayMinutes).sum();
            int weekend = usable.stream().mapToInt(TimesheetEntity::getWeekendMinutes).sum();
            int night = usable.stream().mapToInt(TimesheetEntity::getNightMinutes).sum();
            int workedDays = (int) usable.stream()
                    .flatMap(t -> timesheetLineRepository.findByTimesheetIdOrderByWorkDateAscSortOrderAsc(t.getId()).stream())
                    .filter(l -> l.getPaidMinutes() > 0).map(l -> l.getWorkDate()).distinct().count();
            TimesheetCalculator.Rule rule = usable.stream().map(t -> firstRule(t.getRuleSnapshot())).filter(Objects::nonNull).findFirst()
                    .orElseGet(() -> overtimeRules.apply(end));
            BigDecimal unpaidLeave = unpaidLeaveDays(e, from, to, leaveTypes);

            PayrollCalculator.Earnings earnings = PayrollCalculator.earnings(new PayrollCalculator.EarningsInput(contract.getSalaryType(),
                    contract.getSalaryAmount(), contract.getWeeklyHours(), start, end, from, to, paid, overtime, workedDays, holiday, weekend, night,
                    rule.dailyMultiplier(), rule.holidayMultiplier(), rule.weekendMultiplier(), rule.nightMultiplier(), unpaidLeave));
            issues.addAll(earnings.issues());

            List<PayrollCalculator.Line> earningLines = new ArrayList<>(earnings.lines());
            List<PayrollCalculator.Line> oneOffDeductions = new ArrayList<>();
            for (TipService.PayrollShare share : shares) {
                earningLines.add(new PayrollCalculator.Line(PayslipLineKind.EARNING, share.source() == TipSource.TIPS ? "TIPS" : "SERVICE_CHARGE",
                        (share.source() == TipSource.TIPS ? "Tips share " : "Service charge share ") + share.poolNumber(), null, null,
                        share.amount(), share.taxable()));
            }
            for (PayrollAdjustmentEntity a : mine) {
                if (a.getKind() == AdjustmentKind.EARNING) {
                    earningLines.add(new PayrollCalculator.Line(PayslipLineKind.EARNING, "ADJUSTMENT", a.getDescription(), null, null, a.getAmount(), a.isTaxable()));
                } else {
                    oneOffDeductions.add(new PayrollCalculator.Line(PayslipLineKind.DEDUCTION, "ADJUSTMENT", a.getDescription(), null, null, a.getAmount(), false));
                }
            }
            PayrollCalculator.Result result = PayrollCalculator.apply(earningLines, earnings.basic(),
                    ruleService.resolve(run.getBranchId(), e.getEmploymentType(), end), oneOffDeductions);
            if (result.net().signum() < 0) issues.add("NEGATIVE_NET");

            PayslipEntity slip = new PayslipEntity();
            slip.setRunId(run.getId());
            slip.setEmployeeId(e.getId());
            slip.setContractId(contract.getId());
            slip.setSalaryType(contract.getSalaryType());
            slip.setRate(contract.getSalaryAmount());
            slip.setHourlyRate(earnings.hourlyRate());
            slip.setPaidMinutes(paid);
            slip.setOvertimeMinutes(overtime);
            slip.setWorkedDays(workedDays);
            slip.setUnpaidLeaveDays(unpaidLeave);
            slip.setBasic(result.basic());
            slip.setGross(result.gross());
            slip.setTaxable(result.taxable());
            slip.setEmployeeDeductions(result.employeeDeductions());
            slip.setNet(result.net());
            slip.setEmployerContributions(result.employerContributions());
            slip.setIssues(issues.isEmpty() ? null : String.join(",", issues));
            payslipRepository.save(slip);
            List<PayslipLineEntity> lines = new ArrayList<>();
            for (int i = 0; i < result.lines().size(); i++) {
                lines.add(PayslipLineEntity.of(slip.getId(), result.lines().get(i), i));
            }
            lineRepository.saveAll(lines);
            usable.forEach(t -> t.setPayrollRunId(run.getId()));
            tipService.reserve(shares.stream().map(TipService.PayrollShare::allocationId).toList(), run.getId());

            gross = gross.add(result.gross());
            deductions = deductions.add(result.employeeDeductions());
            net = net.add(result.net());
            employer = employer.add(result.employerContributions());
            count++;
        }
        Set<UUID> paidEmployees = payslipRepository.findByRunId(run.getId()).stream().map(PayslipEntity::getEmployeeId).collect(Collectors.toSet());
        adjustments.keySet().stream().filter(k -> !paidEmployees.contains(k)).filter(k -> skipped.stream().noneMatch(s -> s.employeeId().equals(k)))
                .forEach(k -> skipped.add(new Skipped(k, employeeRepository.findById(k).map(EmployeeEntity::fullName).orElse(null),
                        "Has adjustments but is not paid from this branch in this period")));
        run.setEmployeeCount(count);
        run.setGrossTotal(gross);
        run.setDeductionsTotal(deductions);
        run.setNetTotal(net);
        run.setEmployerTotal(employer);
        run.setSkipped(json(skipped));
        run.setStatus(PayrollRunStatus.CALCULATED);
        run.setCalculatedBy(access.userId());
        run.setCalculatedAt(Instant.now());
        run.setApprovedBy(null);
        run.setApprovedAt(null);
        log(access, run, "calculated", Map.of("payslips", count, "skipped", skipped.size()));
        return detail(runRepository.saveAndFlush(run), access);
    }

    /** Approved unpaid leave inside [from, to], counted the way the leave type counts days (overlap only). */
    private BigDecimal unpaidLeaveDays(EmployeeEntity e, LocalDate from, LocalDate to, Map<UUID, LeaveTypeEntity> types) {
        if (to.isBefore(from)) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequestEntity r : leaveRequestRepository.findOverlapping(e.getId(), EnumSet.of(LeaveStatus.APPROVED), from, to)) {
            LeaveTypeEntity type = types.get(r.getLeaveTypeId());
            if (type == null || type.isPaid()) {
                continue;
            }
            LocalDate s = r.getStartDate().isBefore(from) ? from : r.getStartDate();
            LocalDate en = r.getEndDate().isAfter(to) ? to : r.getEndDate();
            Set<LocalDate> holidays = overtimeSettings.observedHolidays(e.primaryBranchId(), s, en);
            total = total.add(LeaveCalculator.countDays(s, en, r.isStartHalf() && s.equals(r.getStartDate()), r.isEndHalf() && en.equals(r.getEndDate()),
                    type.getCountBasis(), leaveSetup.nonWorkingDays(), holidays));
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private TimesheetCalculator.Rule firstRule(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            List<TimesheetCalculator.Rule> rules = objectMapper.readValue(snapshot, new TypeReference<List<TimesheetCalculator.Rule>>() {});
            return rules.isEmpty() ? null : rules.get(0);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    // ================================================================ adjustments

    @Transactional
    public RunDetail addAdjustment(UserPrincipal principal, UUID runId, AdjustmentRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        PayrollRunEntity run = load(runId, access);
        requireEditable(run);
        EmployeeEntity employee = employeeService.loadInScope(r.employeeId(), access);
        if (!run.getBranchId().equals(employee.primaryBranchId())) {
            throw new ApiException(400, "This employee is not paid from this branch");
        }
        PayrollAdjustmentEntity a = new PayrollAdjustmentEntity();
        a.setRunId(run.getId());
        a.setEmployeeId(employee.getId());
        a.setKind(r.kind());
        a.setTaxable(r.kind() == AdjustmentKind.EARNING && r.taxable());
        a.setDescription(r.description().trim());
        a.setAmount(r.amount().setScale(2, java.math.RoundingMode.HALF_UP));
        a.setCreatedBy(access.userId());
        adjustmentRepository.save(a);
        needsRecalculation(run);
        log(access, run, "adjustment-added", Map.of("employeeId", employee.getId().toString(), "kind", r.kind().name()));
        return detail(runRepository.saveAndFlush(run), access);
    }

    @Transactional
    public RunDetail removeAdjustment(UserPrincipal principal, UUID runId, UUID adjustmentId) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        PayrollRunEntity run = load(runId, access);
        requireEditable(run);
        PayrollAdjustmentEntity a = adjustmentRepository.findById(adjustmentId).filter(x -> x.getRunId().equals(runId))
                .orElseThrow(() -> new ApiException(404, "Adjustment not found"));
        adjustmentRepository.delete(a);
        needsRecalculation(run);
        log(access, run, "adjustment-removed", Map.of("employeeId", a.getEmployeeId().toString()));
        return detail(runRepository.saveAndFlush(run), access);
    }

    private static void requireEditable(PayrollRunEntity run) {
        if (run.getStatus() != PayrollRunStatus.DRAFT && run.getStatus() != PayrollRunStatus.CALCULATED) {
            throw new ApiException(409, "Return the run to draft before changing it");
        }
    }

    /** Any change after calculation sends the run back to draft so nobody approves stale figures. */
    private static void needsRecalculation(PayrollRunEntity run) {
        run.setStatus(PayrollRunStatus.DRAFT);
        run.setApprovedBy(null);
        run.setApprovedAt(null);
    }

    // ================================================================ workflow

    @Transactional
    public RunDetail approve(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_APPROVE);
        PayrollRunEntity run = load(id, access);
        requireStatus(run, PayrollRunStatus.CALCULATED, "Calculate the run before approving it");
        if (access.userId().equals(run.getCalculatedBy())) {
            throw new ApiException(403, "Someone other than the person who calculated this run must approve it");
        }
        List<PayslipEntity> slips = payslipRepository.findByRunId(run.getId());
        if (paysMe(access, slips)) {
            throw new ApiException(403, "You are paid in this run; someone else must approve it");
        }
        long blocked = slips.stream().filter(p -> issues(p).stream().anyMatch(ERRORS::contains)).count();
        if (blocked > 0) {
            throw new ApiException(409, blocked + " payslip" + (blocked == 1 ? " has" : "s have") + " problems to fix before approval");
        }
        if (slips.isEmpty()) {
            throw new ApiException(409, "This run has no payslips");
        }
        run.setStatus(PayrollRunStatus.APPROVED);
        run.setApprovedBy(access.userId());
        run.setApprovedAt(Instant.now());
        log(access, run, "approved", null);
        return detail(runRepository.saveAndFlush(run), access);
    }

    @Transactional
    public RunDetail reopen(UserPrincipal principal, UUID id, ReasonRequest r) {
        HrAccess access = accessService.requireAny(principal, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE);
        PayrollRunEntity run = load(id, access);
        if (run.getStatus() != PayrollRunStatus.CALCULATED && run.getStatus() != PayrollRunStatus.APPROVED) {
            throw new ApiException(409, "Only calculated or approved runs can return to draft");
        }
        needsRecalculation(run);
        timesheetRepository.releaseRun(run.getId());
        tipService.release(run.getId());
        log(access, run, "reopened", Map.of("reason", r.reason().trim()));
        return detail(runRepository.saveAndFlush(run), access);
    }

    @Transactional
    public RunDetail finalizeRun(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_FINALIZE);
        PayrollRunEntity run = load(id, access);
        requireStatus(run, PayrollRunStatus.APPROVED, "Approve the run before finalizing it");
        run.setStatus(PayrollRunStatus.FINALIZED);
        run.setFinalizedBy(access.userId());
        run.setFinalizedAt(Instant.now());
        log(access, run, "finalized", null);
        tipService.payrollFinalized(run.getId());
        List<UUID> employees = payslipRepository.findByRunId(run.getId()).stream().map(PayslipEntity::getEmployeeId).toList();
        List<UUID> logins = employeeRepository.findByIdIn(employees).stream().map(EmployeeEntity::getUserId).filter(Objects::nonNull).toList();
        if (!logins.isEmpty()) {
            push.notifyUsers(logins, "Payslip ready", "Your payslip for " + run.getPeriodStart() + " to " + run.getPeriodEnd() + " is available.",
                    Map.of("type", "PAYSLIP_READY"));
        }
        return detail(runRepository.saveAndFlush(run), access);
    }

    @Transactional
    public RunDetail pay(UserPrincipal principal, UUID id, PayRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PAY);
        PayrollRunEntity run = load(id, access);
        if (run.getStatus() != PayrollRunStatus.FINALIZED) {
            throw new ApiException(409, "Only finalized runs can be paid");
        }
        List<PayslipEntity> slips = payslipRepository.findByRunId(run.getId());
        Set<UUID> chosen = r.payslipIds() == null || r.payslipIds().isEmpty() ? null : Set.copyOf(r.payslipIds());
        if (chosen != null && !slips.stream().map(PayslipEntity::getId).collect(Collectors.toSet()).containsAll(chosen)) {
            throw new ApiException(400, "Some payslips are not part of this run");
        }
        Instant now = Instant.now();
        int marked = 0;
        for (PayslipEntity p : slips) {
            if (p.getPaidAt() == null && (chosen == null || chosen.contains(p.getId()))) {
                p.setPaymentMethod(r.method());
                p.setPaymentReference(blank(r.reference()));
                p.setPaidAt(now);
                p.setPaidBy(access.userId());
                marked++;
            }
        }
        if (marked == 0) {
            throw new ApiException(409, "Nothing left to mark as paid");
        }
        if (slips.stream().allMatch(p -> p.getPaidAt() != null)) {
            run.setStatus(PayrollRunStatus.PAID);
        }
        log(access, run, "paid", Map.of("payslips", marked, "method", r.method().name()));
        return detail(runRepository.saveAndFlush(run), access);
    }

    @Transactional
    public RunDetail cancel(UserPrincipal principal, UUID id, ReasonRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        PayrollRunEntity run = load(id, access);
        if (run.getStatus() == PayrollRunStatus.FINALIZED || run.getStatus() == PayrollRunStatus.PAID || run.getStatus() == PayrollRunStatus.CANCELLED) {
            throw new ApiException(409, "Finalized, paid or cancelled runs cannot be cancelled");
        }
        timesheetRepository.releaseRun(run.getId());
        tipService.release(run.getId());
        run.setStatus(PayrollRunStatus.CANCELLED);
        run.setCancelReason(r.reason().trim());
        log(access, run, "cancelled", Map.of("reason", r.reason().trim()));
        return detail(runRepository.saveAndFlush(run), access);
    }

    // ================================================================ payslips

    @Transactional(readOnly = true)
    public PayslipDetail payslip(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.access(principal);
        PayslipEntity slip = payslipRepository.findById(id).orElseThrow(() -> new ApiException(404, "Payslip not found"));
        PayrollRunEntity run = runRepository.findById(slip.getRunId()).orElseThrow();
        boolean own = employeeRepository.findByUserId(access.userId()).map(e -> e.getId().equals(slip.getEmployeeId())).orElse(false);
        boolean released = run.getStatus() == PayrollRunStatus.FINALIZED || run.getStatus() == PayrollRunStatus.PAID;
        boolean manager = java.util.Arrays.stream(ANY_PAYROLL).anyMatch(access::has) && access.canSeeBranch(run.getBranchId());
        if (!(manager || (own && released))) {
            throw new ApiException(404, "Payslip not found");
        }
        return payslipDetail(slip, run);
    }

    @Transactional(readOnly = true)
    public List<MyPayslip> mine(UserPrincipal principal) {
        EmployeeEntity me = employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
        List<PayslipEntity> slips = payslipRepository.findByEmployeeId(me.getId());
        Map<UUID, PayrollRunEntity> runs = runRepository.findByIdInAndStatusIn(slips.stream().map(PayslipEntity::getRunId).toList(),
                EnumSet.of(PayrollRunStatus.FINALIZED, PayrollRunStatus.PAID)).stream().collect(Collectors.toMap(PayrollRunEntity::getId, Function.identity()));
        return slips.stream().filter(p -> runs.containsKey(p.getRunId()))
                .map(p -> {
                    PayrollRunEntity run = runs.get(p.getRunId());
                    return new MyPayslip(p.getId(), run.getRunNumber(), run.getPeriodStart(), run.getPeriodEnd(), run.getPayDate(), run.getCurrency(),
                            p.getGross(), p.getEmployeeDeductions(), p.getNet(), p.getPaidAt());
                })
                .sorted(Comparator.comparing(MyPayslip::periodStart).reversed())
                .toList();
    }

    // ================================================================ helpers

    private PayrollRunEntity load(UUID id, HrAccess access) {
        PayrollRunEntity run = runRepository.findById(id).orElseThrow(() -> new ApiException(404, "Payroll run not found"));
        if (!access.canSeeBranch(run.getBranchId())) {
            throw new ApiException(404, "Payroll run not found");
        }
        return run;
    }

    private static void requireStatus(PayrollRunEntity run, PayrollRunStatus status, String message) {
        if (run.getStatus() != status) {
            throw new ApiException(409, message);
        }
    }

    private boolean paysMe(HrAccess access, List<PayslipEntity> slips) {
        return employeeRepository.findByUserId(access.userId())
                .map(me -> slips.stream().anyMatch(p -> p.getEmployeeId().equals(me.getId()))).orElse(false);
    }

    /** Audit without figures: amounts never go to the shared log. */
    private void log(HrAccess access, PayrollRunEntity run, String operation, Map<String, ?> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", operation);
        meta.put("runNumber", run.getRunNumber());
        meta.put("from", run.getPeriodStart().toString());
        meta.put("to", run.getPeriodEnd().toString());
        if (extra != null) meta.putAll(extra);
        audit.log(access.userId(), AuditAction.HR_PAYROLL_CHANGED, "PayrollRun", run.getId(), meta);
    }

    private RunDetail detail(PayrollRunEntity run, HrAccess access) {
        List<PayslipEntity> slips = payslipRepository.findByRunId(run.getId());
        Set<UUID> ids = new java.util.HashSet<>(slips.stream().map(PayslipEntity::getEmployeeId).toList());
        List<PayrollAdjustmentEntity> adjustments = adjustmentRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
        adjustments.forEach(a -> ids.add(a.getEmployeeId()));
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(ids).stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        List<PayslipSummary> payslips = slips.stream().map(p -> payslipSummary(p, employees.get(p.getEmployeeId())))
                .sorted(Comparator.comparing(PayslipSummary::employeeName, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        List<AdjustmentView> adjustmentViews = adjustments.stream().map(a -> new AdjustmentView(a.getId(), a.getEmployeeId(),
                employees.containsKey(a.getEmployeeId()) ? employees.get(a.getEmployeeId()).fullName() : null, a.getKind(), a.isTaxable(),
                a.getDescription(), a.getAmount(), a.getCreatedAt())).toList();
        int blocking = (int) payslips.stream().filter(p -> p.issues().stream().anyMatch(ERRORS::contains)).count();
        return new RunDetail(summary(run, branchNames(), slips), payslips, adjustmentViews, skipped(run.getSkipped()), blocking,
                run.getCalculatedBy() != null && run.getCalculatedBy().equals(access.userId()));
    }

    private RunSummary summary(PayrollRunEntity r, Map<UUID, String> branches, List<PayslipEntity> slips) {
        Integer paidCount = slips == null ? null : (int) slips.stream().filter(p -> p.getPaidAt() != null).count();
        return new RunSummary(r.getId(), r.getRunNumber(), r.getBranchId(), branches.get(r.getBranchId()), r.getPeriodStart(), r.getPeriodEnd(),
                r.getPayDate(), r.getStatus(), r.getCurrency(), r.getEmployeeCount(), r.getGrossTotal(), r.getDeductionsTotal(), r.getNetTotal(),
                r.getEmployerTotal(), paidCount, r.getCalculatedAt(), r.getApprovedAt(), r.getFinalizedAt(), r.getCancelReason(), r.getNotes(),
                r.getVersion());
    }

    private static PayslipSummary payslipSummary(PayslipEntity p, EmployeeEntity e) {
        return new PayslipSummary(p.getId(), p.getRunId(), p.getEmployeeId(), e == null ? null : e.fullName(), e == null ? null : e.getEmployeeNumber(),
                p.getSalaryType(), p.getRate(), p.getPaidMinutes(), p.getOvertimeMinutes(), p.getWorkedDays(), p.getUnpaidLeaveDays(), p.getBasic(),
                p.getGross(), p.getTaxable(), p.getEmployeeDeductions(), p.getNet(), p.getEmployerContributions(), issues(p), p.getPaymentMethod(),
                p.getPaymentReference(), p.getPaidAt());
    }

    private PayslipDetail payslipDetail(PayslipEntity p, PayrollRunEntity run) {
        EmployeeEntity e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
        List<LineView> lines = lineRepository.findByPayslipIdOrderBySortOrderAsc(p.getId()).stream()
                .map(l -> new LineView(l.getKind(), l.getCode(), l.getDescription(), l.getQuantity(), l.getRate(), l.getAmount())).toList();
        return new PayslipDetail(payslipSummary(p, e), run.getRunNumber(), run.getStatus(), run.getPeriodStart(), run.getPeriodEnd(), run.getPayDate(),
                run.getCurrency(), branchNames().get(run.getBranchId()), p.getHourlyRate(), lines);
    }

    static List<String> issues(PayslipEntity p) {
        return p.getIssues() == null ? List.of() : List.of(p.getIssues().split(","));
    }

    private Map<UUID, String> branchNames() {
        Map<UUID, String> names = new HashMap<>();
        branchRepository.findAll().forEach(b -> names.put(b.getId(), b.getName()));
        return names;
    }

    private List<Skipped> skipped(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Skipped>>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static LocalDate latest(LocalDate a, LocalDate... others) {
        LocalDate result = a;
        for (LocalDate d : others) {
            if (d != null && d.isAfter(result)) result = d;
        }
        return result;
    }

    private static LocalDate earliest(LocalDate a, LocalDate... others) {
        LocalDate result = a;
        for (LocalDate d : others) {
            if (d != null && d.isBefore(result)) result = d;
        }
        return result;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ================================================================ DTOs

    public record CreateRunRequest(@NotNull UUID branchId, @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, LocalDate payDate,
                                   @Size(max = 500) String notes) {}

    public record AdjustmentRequest(@NotNull UUID employeeId, @NotNull AdjustmentKind kind, boolean taxable,
                                    @NotBlank @Size(max = 200) String description, @NotNull @DecimalMin("0.01") BigDecimal amount) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record PayRequest(List<UUID> payslipIds, @NotNull PaymentMethod method, @Size(max = 120) String reference) {}

    public record Skipped(UUID employeeId, String name, String reason) {}

    public record RunSummary(UUID id, String runNumber, UUID branchId, String branchName, LocalDate periodStart, LocalDate periodEnd,
                             LocalDate payDate, PayrollRunStatus status, String currency, int employeeCount, BigDecimal grossTotal,
                             BigDecimal deductionsTotal, BigDecimal netTotal, BigDecimal employerTotal, Integer paidCount,
                             Instant calculatedAt, Instant approvedAt, Instant finalizedAt, String cancelReason, String notes, long version) {}

    public record PayslipSummary(UUID id, UUID runId, UUID employeeId, String employeeName, String employeeNumber, SalaryType salaryType,
                                 BigDecimal rate, int paidMinutes, int overtimeMinutes, int workedDays, BigDecimal unpaidLeaveDays,
                                 BigDecimal basic, BigDecimal gross, BigDecimal taxable, BigDecimal employeeDeductions, BigDecimal net,
                                 BigDecimal employerContributions, List<String> issues, PaymentMethod paymentMethod, String paymentReference,
                                 Instant paidAt) {}

    public record AdjustmentView(UUID id, UUID employeeId, String employeeName, AdjustmentKind kind, boolean taxable, String description,
                                 BigDecimal amount, Instant createdAt) {}

    public record RunDetail(RunSummary summary, List<PayslipSummary> payslips, List<AdjustmentView> adjustments, List<Skipped> skipped,
                            int blockingPayslips, boolean calculatedByMe) {}

    public record LineView(PayslipLineKind kind, String code, String description, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {}

    public record PayslipDetail(PayslipSummary summary, String runNumber, PayrollRunStatus runStatus, LocalDate periodStart, LocalDate periodEnd,
                                LocalDate payDate, String currency, String branchName, BigDecimal hourlyRate, List<LineView> lines) {}

    public record MyPayslip(UUID id, String runNumber, LocalDate periodStart, LocalDate periodEnd, LocalDate payDate, String currency,
                            BigDecimal gross, BigDecimal deductions, BigDecimal net, Instant paidAt) {}
}
