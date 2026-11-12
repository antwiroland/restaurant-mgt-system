package com.restaurantmanager.core.hr.report;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.attendance.AttendanceCorrectionRepository;
import com.restaurantmanager.core.hr.attendance.AttendanceManagementService;
import com.restaurantmanager.core.hr.attendance.CorrectionStatus;
import com.restaurantmanager.core.hr.contract.ContractEntity;
import com.restaurantmanager.core.hr.contract.ContractRepository;
import com.restaurantmanager.core.hr.document.EmployeeDocumentEntity;
import com.restaurantmanager.core.hr.document.EmployeeDocumentRepository;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmploymentStatus;
import com.restaurantmanager.core.hr.leave.LeaveRequestRepository;
import com.restaurantmanager.core.hr.leave.LeaveStatus;
import com.restaurantmanager.core.hr.payroll.PayrollRunEntity;
import com.restaurantmanager.core.hr.payroll.PayrollRunRepository;
import com.restaurantmanager.core.hr.payroll.PayrollRunStatus;
import com.restaurantmanager.core.hr.recruitment.ApplicationEntity;
import com.restaurantmanager.core.hr.recruitment.ApplicationRepository;
import com.restaurantmanager.core.hr.recruitment.InterviewOutcome;
import com.restaurantmanager.core.hr.recruitment.InterviewRepository;
import com.restaurantmanager.core.hr.recruitment.VacancyEntity;
import com.restaurantmanager.core.hr.recruitment.VacancyRepository;
import com.restaurantmanager.core.hr.recruitment.VacancyStatus;
import com.restaurantmanager.core.hr.schedule.SwapRequestRepository;
import com.restaurantmanager.core.hr.schedule.SwapStatus;
import com.restaurantmanager.core.hr.timesheet.TimesheetEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetStatus;
import com.restaurantmanager.core.hr.tips.TipPoolRepository;
import com.restaurantmanager.core.hr.tips.TipPoolStatus;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The HR dashboard: one read of the workforce for the viewer's branches. Each section is included only when the viewer may
 * see the underlying data (attendance, contracts, documents, recruitment, pay), so the dashboard never widens access.
 */
@Service
public class HrDashboardService {
    static final int EXPIRY_WINDOW_DAYS = 30;
    static final int TREND_MONTHS = 6;
    private static final Set<EmploymentStatus> EMPLOYED = EnumSet.of(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE, EmploymentStatus.SUSPENDED);
    private static final List<PaymentStatus> COLLECTED = List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    private final HrAccessService accessService;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceManagementService attendance;
    private final AttendanceCorrectionRepository correctionRepository;
    private final LeaveRequestRepository leaveRepository;
    private final TimesheetRepository timesheetRepository;
    private final SwapRequestRepository swapRepository;
    private final ContractRepository contractRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final PayrollRunRepository runRepository;
    private final TipPoolRepository poolRepository;
    private final VacancyRepository vacancyRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final PaymentRepository paymentRepository;
    private final ZoneId zone;

    public HrDashboardService(HrAccessService accessService, BranchRepository branchRepository, EmployeeRepository employeeRepository,
                              AttendanceManagementService attendance, AttendanceCorrectionRepository correctionRepository,
                              LeaveRequestRepository leaveRepository, TimesheetRepository timesheetRepository, SwapRequestRepository swapRepository,
                              ContractRepository contractRepository, EmployeeDocumentRepository documentRepository, PayrollRunRepository runRepository,
                              TipPoolRepository poolRepository, VacancyRepository vacancyRepository, ApplicationRepository applicationRepository,
                              InterviewRepository interviewRepository, PaymentRepository paymentRepository,
                              @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.accessService = accessService;
        this.branchRepository = branchRepository;
        this.employeeRepository = employeeRepository;
        this.attendance = attendance;
        this.correctionRepository = correctionRepository;
        this.leaveRepository = leaveRepository;
        this.timesheetRepository = timesheetRepository;
        this.swapRepository = swapRepository;
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.runRepository = runRepository;
        this.poolRepository = poolRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.paymentRepository = paymentRepository;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(UserPrincipal principal, UUID branchId) {
        HrAccess access = accessService.require(principal, HrPermission.REPORTS_VIEW);
        if (branchId != null) {
            access.assertBranch(branchId);
        }
        LocalDate today = LocalDate.now(zone);
        List<BranchEntity> visibleBranches = branchRepository.findAll().stream()
                .filter(b -> access.canSeeBranch(b.getId()))
                .sorted(Comparator.comparing(BranchEntity::getName))
                .toList();
        Map<UUID, String> branchNames = visibleBranches.stream().collect(Collectors.toMap(BranchEntity::getId, BranchEntity::getName));
        Set<UUID> scope = branchId != null ? Set.of(branchId) : branchNames.keySet();
        // employees without a home branch are organisation-level: only counted for central viewers looking at every branch
        boolean includeCentral = branchId == null && access.allBranches();

        List<EmployeeEntity> employees = employeeRepository.findAll().stream()
                .filter(e -> e.primaryBranchId() == null ? includeCentral : scope.contains(e.primaryBranchId()))
                .toList();
        Map<UUID, EmployeeEntity> byId = employees.stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        boolean payVisible = access.has(HrPermission.PAYROLL_VIEW) || access.has(HrPermission.COMPENSATION_VIEW);

        return new Dashboard(today, branchId, visibleBranches.stream().map(b -> new BranchRef(b.getId(), b.getName())).toList(),
                headcount(employees, today, branchNames), movement(employees, today), trend(employees, today),
                access.has(HrPermission.ATTENDANCE_VIEW) ? today(principal, scope, today) : null,
                actions(access, scope, byId, today),
                expiring(access, byId, today),
                access.has(HrPermission.RECRUITMENT_MANAGE) ? recruitment(scope, today) : null,
                payVisible ? labour(scope, branchNames, today) : List.of(),
                payVisible);
    }

    // ---------------------------------------------------------------- workforce

    private Headcount headcount(List<EmployeeEntity> employees, LocalDate today, Map<UUID, String> branchNames) {
        List<EmployeeEntity> employed = employees.stream()
                .filter(e -> EMPLOYED.contains(e.getEmploymentStatus()) && !e.getHireDate().isAfter(today)).toList();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (EmploymentStatus s : EMPLOYED) {
            byStatus.put(s.name(), employed.stream().filter(e -> e.getEmploymentStatus() == s).count());
        }
        long startingSoon = employees.stream().filter(e -> EMPLOYED.contains(e.getEmploymentStatus()) && e.getHireDate().isAfter(today)).count();
        return new Headcount(employed.size(), startingSoon, byStatus,
                slices(employed, e -> e.getDepartment() == null ? "No department" : e.getDepartment().getName()),
                slices(employed, e -> e.getEmploymentType().name()),
                slices(employed, e -> e.primaryBranchId() == null ? "Central" : branchNames.getOrDefault(e.primaryBranchId(), "Other")));
    }

    private static List<Slice> slices(List<EmployeeEntity> employees, Function<EmployeeEntity, String> key) {
        return employees.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting())).entrySet().stream()
                .map(e -> new Slice(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Slice::count).reversed().thenComparing(Slice::label))
                .toList();
    }

    private Movement movement(List<EmployeeEntity> employees, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearAgo = today.minusYears(1);
        List<PersonEvent> joiners = employees.stream()
                .filter(e -> !e.getHireDate().isBefore(monthStart) && !e.getHireDate().isAfter(today.plusDays(EXPIRY_WINDOW_DAYS)))
                .sorted(Comparator.comparing(EmployeeEntity::getHireDate))
                .map(e -> new PersonEvent(e.getId(), e.fullName(), e.getHireDate(), positionTitle(e)))
                .toList();
        List<PersonEvent> leavers = employees.stream()
                .filter(e -> e.getExitDate() != null && !e.getExitDate().isBefore(monthStart.minusMonths(1)) && !e.getExitDate().isAfter(today))
                .sorted(Comparator.comparing(EmployeeEntity::getExitDate).reversed())
                .map(e -> new PersonEvent(e.getId(), e.fullName(), e.getExitDate(), e.getEmploymentStatus().name()))
                .toList();
        long leftInYear = employees.stream().filter(e -> e.getExitDate() != null && e.getExitDate().isAfter(yearAgo) && !e.getExitDate().isAfter(today))
                .count();
        double average = (employedOn(employees, yearAgo) + employedOn(employees, today)) / 2.0;
        BigDecimal turnover = average <= 0 ? null : BigDecimal.valueOf(leftInYear * 100.0 / average).setScale(1, RoundingMode.HALF_UP);
        return new Movement(joiners, leavers, leftInYear, turnover);
    }

    private List<TrendPoint> trend(List<EmployeeEntity> employees, LocalDate today) {
        List<TrendPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            LocalDate start = month.atDay(1);
            LocalDate end = i == 0 ? today : month.atEndOfMonth();
            long joined = employees.stream().filter(e -> !e.getHireDate().isBefore(start) && !e.getHireDate().isAfter(end)).count();
            long left = employees.stream().filter(e -> e.getExitDate() != null && !e.getExitDate().isBefore(start) && !e.getExitDate().isAfter(end)).count();
            points.add(new TrendPoint(month.toString(), employedOn(employees, end), joined, left));
        }
        return points;
    }

    /** On the payroll that day: hired by then and not yet past their exit date. */
    private static long employedOn(List<EmployeeEntity> employees, LocalDate day) {
        return employees.stream().filter(e -> !e.getHireDate().isAfter(day) && (e.getExitDate() == null || !e.getExitDate().isBefore(day))).count();
    }

    // ---------------------------------------------------------------- today

    private Today today(UserPrincipal principal, Set<UUID> scope, LocalDate today) {
        Map<String, Long> counts = new TreeMap<>();
        long late = 0;
        for (UUID branch : scope) {
            AttendanceManagementService.DayView day = attendance.day(principal, branch, today);
            day.counts().forEach((k, v) -> counts.merge(k, v, Long::sum));
            late += day.lateArrivals();
        }
        return new Today(counts, late);
    }

    // ---------------------------------------------------------------- work waiting

    private List<ActionItem> actions(HrAccess access, Set<UUID> scope, Map<UUID, EmployeeEntity> employees, LocalDate today) {
        List<ActionItem> items = new ArrayList<>();
        if (access.has(HrPermission.LEAVE_APPROVE) || access.has(HrPermission.LEAVE_MANAGE)) {
            long n = leaveRepository.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING).stream()
                    .filter(r -> employees.containsKey(r.getEmployeeId())).count();
            items.add(new ActionItem("LEAVE_PENDING", "Leave requests waiting", n, "/hr/leave"));
        }
        if (access.has(HrPermission.ATTENDANCE_CORRECT)) {
            long n = correctionRepository.findByStatusOrderByCreatedAtAsc(CorrectionStatus.PENDING).stream()
                    .filter(c -> c.getBranchId() != null && scope.contains(c.getBranchId())).count();
            items.add(new ActionItem("CORRECTIONS_PENDING", "Attendance corrections to approve", n, "/hr/attendance"));
        }
        if (access.has(HrPermission.TIMESHEET_REVIEW) && !scope.isEmpty()) {
            long n = timesheetRepository.findAtBranches(scope, today.minusDays(93), today).stream()
                    .filter(t -> t.getStatus() == TimesheetStatus.SUBMITTED || t.getStatus() == TimesheetStatus.REVIEWED).count();
            items.add(new ActionItem("TIMESHEETS_TO_REVIEW", "Timesheets to review or approve", n, "/hr/timesheets"));
        }
        if (access.has(HrPermission.SHIFT_SWAP_APPROVE)) {
            long n = swapRepository.findByStatusOrderByCreatedAtAsc(SwapStatus.PENDING).stream()
                    .filter(s -> employees.containsKey(s.getRequesterEmployeeId())).count();
            items.add(new ActionItem("SWAPS_PENDING", "Shift swaps to decide", n, "/hr/schedule"));
        }
        if (access.has(HrPermission.PAYROLL_APPROVE) || access.has(HrPermission.PAYROLL_FINALIZE) || access.has(HrPermission.PAYROLL_PAY)) {
            List<PayrollRunEntity> runs = scope.isEmpty() ? List.of() : runRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(scope);
            if (access.has(HrPermission.PAYROLL_APPROVE)) {
                items.add(new ActionItem("PAYROLL_TO_APPROVE", "Payroll runs to approve", count(runs, PayrollRunStatus.CALCULATED), "/hr/payroll"));
            }
            if (access.has(HrPermission.PAYROLL_FINALIZE)) {
                items.add(new ActionItem("PAYROLL_TO_FINALIZE", "Payroll runs to finalize", count(runs, PayrollRunStatus.APPROVED), "/hr/payroll"));
            }
            if (access.has(HrPermission.PAYROLL_PAY)) {
                items.add(new ActionItem("PAYROLL_TO_PAY", "Payroll runs to pay", count(runs, PayrollRunStatus.FINALIZED), "/hr/payroll"));
            }
        }
        if (access.has(HrPermission.TIPS_MANAGE) || access.has(HrPermission.SERVICE_CHARGE_MANAGE)) {
            long n = scope.isEmpty() ? 0 : poolRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(scope).stream()
                    .filter(p -> p.getStatus() == TipPoolStatus.CALCULATED).count();
            items.add(new ActionItem("POOLS_TO_APPROVE", "Tip and service charge pools to approve", n, "/hr/tips"));
        }
        return items;
    }

    private static long count(List<PayrollRunEntity> runs, PayrollRunStatus status) {
        return runs.stream().filter(r -> r.getStatus() == status).count();
    }

    // ---------------------------------------------------------------- expiring

    private List<ExpiryItem> expiring(HrAccess access, Map<UUID, EmployeeEntity> employees, LocalDate today) {
        LocalDate until = today.plusDays(EXPIRY_WINDOW_DAYS);
        List<ExpiryItem> items = new ArrayList<>();
        if (access.has(HrPermission.CONTRACT_VIEW)) {
            Map<UUID, ContractEntity> contracts = new LinkedHashMap<>();
            contractRepository.findActiveEndedBefore(today).forEach(c -> contracts.put(c.getId(), c));
            contractRepository.findActiveWithDatesDue(today, until).forEach(c -> contracts.putIfAbsent(c.getId(), c));
            for (ContractEntity c : contracts.values()) {
                EmployeeEntity e = employees.get(c.getEmployeeId());
                if (e == null || !EMPLOYED.contains(e.getEmploymentStatus())) continue;
                if (c.getEndDate() != null && !c.getEndDate().isAfter(until)) {
                    items.add(new ExpiryItem("CONTRACT_END", e.getId(), e.fullName(), "Contract " + c.getContractNumber() + " ends", c.getEndDate(),
                            c.getEndDate().isBefore(today)));
                }
                if (c.getProbationEndDate() != null && !c.getProbationEndDate().isBefore(today) && !c.getProbationEndDate().isAfter(until)) {
                    items.add(new ExpiryItem("PROBATION_END", e.getId(), e.fullName(), "Probation ends", c.getProbationEndDate(), false));
                }
            }
        }
        if (access.has(HrPermission.DOCUMENT_VIEW)) {
            for (EmployeeDocumentEntity d : documentRepository.findByArchivedFalseAndExpiresOnLessThanEqualOrderByExpiresOnAsc(until)) {
                EmployeeEntity e = employees.get(d.getEmployeeId());
                if (e == null || !EMPLOYED.contains(e.getEmploymentStatus())) continue;
                items.add(new ExpiryItem("DOCUMENT", e.getId(), e.fullName(), d.getTitle(), d.getExpiresOn(), d.getExpiresOn().isBefore(today)));
            }
        }
        items.sort(Comparator.comparing(ExpiryItem::date).thenComparing(ExpiryItem::employeeName));
        return items;
    }

    // ---------------------------------------------------------------- recruitment

    private Recruitment recruitment(Set<UUID> scope, LocalDate today) {
        List<VacancyEntity> vacancies = scope.isEmpty() ? List.of() : vacancyRepository.findByBranchIdInOrderByCreatedAtDesc(scope);
        List<VacancyEntity> open = vacancies.stream().filter(v -> v.getStatus() == VacancyStatus.OPEN).toList();
        List<ApplicationEntity> apps = open.isEmpty() ? List.of() : applicationRepository.findByVacancyIdIn(open.stream().map(VacancyEntity::getId).toList());
        Map<String, Long> pipeline = new LinkedHashMap<>();
        apps.stream().filter(a -> !a.getStage().isClosed())
                .forEach(a -> pipeline.merge(a.getStage().name(), 1L, Long::sum));
        Instant from = Instant.now();
        Instant to = today.plusDays(8).atStartOfDay(zone).toInstant();
        long interviews = apps.isEmpty() ? 0 : interviewRepository.findByApplicationIdIn(apps.stream().map(ApplicationEntity::getId).toList()).stream()
                .filter(i -> i.getOutcome() == InterviewOutcome.SCHEDULED && !i.getScheduledAt().isBefore(from) && i.getScheduledAt().isBefore(to))
                .count();
        int openings = open.stream().mapToInt(v -> Math.max(0, v.getOpenings() - v.getHiredCount())).sum();
        return new Recruitment(open.size(), open.stream().filter(VacancyEntity::isPublished).count(), openings, pipeline, interviews);
    }

    // ---------------------------------------------------------------- labour cost

    /** The latest finalized or paid payroll per branch, against the sales collected in the same period. */
    private List<LabourCost> labour(Set<UUID> scope, Map<UUID, String> branchNames, LocalDate today) {
        if (scope.isEmpty()) return List.of();
        Map<UUID, PayrollRunEntity> latest = new HashMap<>();
        for (PayrollRunEntity r : runRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(scope)) {
            if (r.getStatus() == PayrollRunStatus.FINALIZED || r.getStatus() == PayrollRunStatus.PAID) {
                latest.putIfAbsent(r.getBranchId(), r);
            }
        }
        List<LabourCost> rows = new ArrayList<>();
        for (PayrollRunEntity r : latest.values()) {
            BigDecimal gross = nz(r.getGrossTotal());
            BigDecimal employer = nz(r.getEmployerTotal());
            BigDecimal cost = gross.add(employer);
            BigDecimal sales = sales(r.getBranchId(), r.getPeriodStart(), r.getPeriodEnd());
            BigDecimal percent = sales.signum() > 0 ? cost.multiply(BigDecimal.valueOf(100)).divide(sales, 1, RoundingMode.HALF_UP) : null;
            int overtime = timesheetRepository.findByPayrollRunId(r.getId()).stream().mapToInt(TimesheetEntity::payableOvertimeMinutes).sum();
            rows.add(new LabourCost(r.getBranchId(), branchNames.get(r.getBranchId()), r.getRunNumber(), r.getPeriodStart(), r.getPeriodEnd(),
                    r.getStatus().name(), r.getEmployeeCount(), gross, employer, cost, sales, percent, overtime));
        }
        rows.sort(Comparator.comparing(LabourCost::branchName, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    /** Money collected for the branch's orders in the period, net of refunds and excluding cancelled orders. */
    private BigDecimal sales(UUID branchId, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentEntity p : paymentRepository.findPaidBetween(COLLECTED, start, end)) {
            OrderEntity o = p.getOrder();
            if (o == null || o.getBranch() == null || !branchId.equals(o.getBranch().getId()) || o.getStatus() == OrderStatus.CANCELLED) continue;
            total = total.add(nz(p.getAmount())).subtract(nz(p.getRefundedAmount()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String positionTitle(EmployeeEntity e) {
        return e.getPosition() == null ? null : e.getPosition().getTitle();
    }

    // ---------------------------------------------------------------- views

    public record Dashboard(LocalDate asOf, UUID branchId, List<BranchRef> branches, Headcount headcount, Movement movement,
                            List<TrendPoint> trend, Today today, List<ActionItem> actions, List<ExpiryItem> expiring,
                            Recruitment recruitment, List<LabourCost> labour, boolean payVisible) {}

    public record BranchRef(UUID id, String name) {}

    public record Slice(String label, long count) {}

    /** {@code employed}: hired by today and not exited (active, on leave or suspended). */
    public record Headcount(long employed, long startingSoon, Map<String, Long> byStatus, List<Slice> byDepartment, List<Slice> byEmploymentType,
                            List<Slice> byBranch) {}

    public record PersonEvent(UUID employeeId, String name, LocalDate date, String detail) {}

    /** Joiners from the 1st of this month (incl. the next 30 days); leavers since the 1st of last month; turnover over 12 months. */
    public record Movement(List<PersonEvent> joiners, List<PersonEvent> leavers, long leaversLast12Months, BigDecimal turnoverPercent) {}

    public record TrendPoint(String month, long headcount, long joined, long left) {}

    /** Attendance-board statuses summed over the branches in view (e.g. WORKING, ON_BREAK, ABSENT, ON_LEAVE, NOT_STARTED). */
    public record Today(Map<String, Long> counts, long lateArrivals) {}

    public record ActionItem(String key, String label, long count, String href) {}

    public record ExpiryItem(String kind, UUID employeeId, String employeeName, String detail, LocalDate date, boolean overdue) {}

    public record Recruitment(long openVacancies, long published, long openingsLeft, Map<String, Long> pipeline, long interviewsNext7Days) {}

    public record LabourCost(UUID branchId, String branchName, String runNumber, LocalDate periodStart, LocalDate periodEnd, String status,
                             int employees, BigDecimal grossPay, BigDecimal employerCost, BigDecimal totalCost, BigDecimal sales,
                             BigDecimal labourPercent, int overtimeMinutes) {}
}
