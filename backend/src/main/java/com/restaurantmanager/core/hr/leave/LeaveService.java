package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.document.DocumentType;
import com.restaurantmanager.core.hr.document.HrDocumentService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.hr.timesheet.OvertimeSettingsService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leave balances (one row per employee, type and year, explained by an append-only ledger) and leave requests with a
 * configurable, per-request approval chain.
 *
 * <p>Balance movements: a request reserves its days as <em>pending</em>; final approval moves them to <em>used</em>;
 * rejection or cancellation of a pending request releases them; cancelling approved leave restores them.</p>
 */
@Service
public class LeaveService {
    static final Set<LeaveStatus> ACTIVE = EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);
    private static final int MAX_CALENDAR_DAYS = 93;

    private final LeaveTypeRepository typeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveRequestRepository requestRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final LeaveTransactionRepository transactionRepository;
    private final LeaveSetupService setup;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final OvertimeSettingsService overtimeSettings;
    private final HrDocumentService documentService;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final LeaveNotifier notifier;
    private final ZoneId zone;

    public LeaveService(LeaveTypeRepository typeRepository, LeaveBalanceRepository balanceRepository, LeaveRequestRepository requestRepository,
                        LeaveApprovalRepository approvalRepository, LeaveTransactionRepository transactionRepository, LeaveSetupService setup,
                        EmployeeRepository employeeRepository, EmployeeService employeeService, UserRepository userRepository,
                        WorkScheduleRepository scheduleRepository, OvertimeSettingsService overtimeSettings, HrDocumentService documentService,
                        HrAccessService accessService, HrAuditService audit, LeaveNotifier notifier,
                        @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.typeRepository = typeRepository;
        this.balanceRepository = balanceRepository;
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.transactionRepository = transactionRepository;
        this.setup = setup;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.overtimeSettings = overtimeSettings;
        this.documentService = documentService;
        this.accessService = accessService;
        this.audit = audit;
        this.notifier = notifier;
        this.zone = ZoneId.of(timeZone);
    }

    private LocalDate today() {
        return LocalDate.now(zone);
    }

    // ================================================================ balances

    @Transactional(readOnly = true)
    public List<BalanceView> balances(UserPrincipal principal, UUID employeeId, Integer year) {
        HrAccess access = accessService.requireAny(principal, HrPermission.LEAVE_APPROVE, HrPermission.LEAVE_MANAGE);
        EmployeeEntity employee = employeeService.loadVisible(employeeId, access);
        return balancesOf(employee, year == null ? today().getYear() : year);
    }

    @Transactional(readOnly = true)
    public List<BalanceView> myBalances(UserPrincipal principal, Integer year) {
        return balancesOf(me(principal), year == null ? today().getYear() : year);
    }

    /** Every balance-tracked type, using the stored balance or, when none exists yet, the one it would be opened with. */
    private List<BalanceView> balancesOf(EmployeeEntity employee, int year) {
        Map<UUID, LeaveBalanceEntity> stored = balanceRepository.findByEmployeeIdAndLeaveYear(employee.getId(), year).stream()
                .collect(Collectors.toMap(LeaveBalanceEntity::getLeaveTypeId, Function.identity()));
        LocalDate asOf = year == today().getYear() ? today() : LocalDate.of(year, 12, 31);
        List<BalanceView> views = new ArrayList<>();
        for (LeaveTypeEntity type : typeRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            LeaveBalanceEntity balance = stored.get(type.getId());
            if (!type.isDeductsBalance() || (!type.isActive() && balance == null)) {
                continue;
            }
            views.add(toView(balance != null ? balance : project(employee, type, year).balance(), type, asOf));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public List<TransactionView> transactions(UserPrincipal principal, UUID balanceId) {
        LeaveBalanceEntity balance = balanceRepository.findById(balanceId).orElseThrow(() -> new ApiException(404, "Balance not found"));
        HrAccess access = accessService.access(principal);
        boolean own = employeeRepository.findByUserId(access.userId()).map(e -> e.getId().equals(balance.getEmployeeId())).orElse(false);
        if (!own) {
            if (!access.has(HrPermission.LEAVE_APPROVE) && !access.has(HrPermission.LEAVE_MANAGE)) {
                throw new ApiException(404, "Balance not found");
            }
            try {
                employeeService.loadVisible(balance.getEmployeeId(), access);
            } catch (ApiException ex) {
                throw new ApiException(404, "Balance not found");
            }
        }
        List<LeaveTransactionEntity> rows = transactionRepository.findByBalanceIdOrderByCreatedAtAsc(balanceId);
        Map<UUID, String> names = userNames(rows.stream().map(LeaveTransactionEntity::getActorId).toList());
        return rows.stream().map(t -> new TransactionView(t.getId(), t.getKind(), t.getDays(), t.getRequestId(), t.getNote(),
                names.get(t.getActorId()), t.getCreatedAt())).toList();
    }

    @Transactional
    public BalanceView adjust(UserPrincipal principal, UUID employeeId, AdjustRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.LEAVE_MANAGE);
        EmployeeEntity employee = employeeService.loadInScope(employeeId, access);
        if (r.days().signum() == 0) {
            throw new ApiException(400, "An adjustment cannot be zero days");
        }
        LeaveTypeEntity type = setup.loadType(r.leaveTypeId());
        if (!type.isDeductsBalance()) {
            throw new ApiException(400, type.getName() + " has no balance to adjust");
        }
        LeaveBalanceEntity balance = lockedBalance(employee, type, r.year(), access.userId());
        balance.setAdjustmentDays(balance.getAdjustmentDays().add(r.days()));
        LocalDate asOf = r.year() == today().getYear() ? today() : LocalDate.of(r.year(), 12, 31);
        if (available(balance, asOf).signum() < 0 && r.days().signum() < 0 && !type.isAllowNegative()) {
            throw new ApiException(409, "This adjustment would leave a negative " + type.getName().toLowerCase() + " balance");
        }
        transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), LeaveTxKind.ADJUSTMENT, r.days(), null, r.note().trim(), access.userId()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("leaveType", type.getCode());
        meta.put("year", r.year());
        meta.put("days", r.days().toPlainString());
        meta.put("note", r.note().trim());
        audit.log(access.userId(), AuditAction.HR_LEAVE_BALANCE_ADJUSTED, "Employee", employee.getId(), meta);
        return toView(balanceRepository.saveAndFlush(balance), type, asOf);
    }

    /** What a new balance for the year would hold: pro-rated entitlement from the policy plus carry-over from last year. */
    private Projection project(EmployeeEntity employee, LeaveTypeEntity type, int year) {
        LeaveBalanceEntity balance = new LeaveBalanceEntity();
        balance.setEmployeeId(employee.getId());
        balance.setLeaveTypeId(type.getId());
        balance.setLeaveYear(year);
        LocalDate hire = employee.getHireDate();
        balance.setAccrualStart(hire != null && hire.getYear() == year ? hire : LocalDate.of(year, 1, 1));
        Optional<LeavePolicyEntity> policy = setup.resolvePolicy(type.getId(), employee, year);
        policy.ifPresent(p -> {
            balance.setPolicyId(p.getId());
            balance.setAccrual(p.getAccrual());
            balance.setEntitledDays(LeaveCalculator.entitlement(p.getAnnualDays(), year, hire));
            balanceRepository.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employee.getId(), type.getId(), year - 1).ifPresent(prev ->
                    balance.setCarriedOverDays(LeaveCalculator.carryOver(available(prev, LocalDate.of(year - 1, 12, 31)), p.getCarryOverMaxDays())));
        });
        return new Projection(balance, policy.orElse(null));
    }

    /** Opens the year's balance on first use (writing the entitlement and carry-over to the ledger) and locks the row. */
    private LeaveBalanceEntity lockedBalance(EmployeeEntity employee, LeaveTypeEntity type, int year, UUID actorId) {
        Optional<LeaveBalanceEntity> existing = balanceRepository.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employee.getId(), type.getId(), year);
        if (existing.isPresent()) {
            return balanceRepository.lockById(existing.get().getId()).orElseThrow();
        }
        Projection projection = project(employee, type, year);
        LeaveBalanceEntity balance = balanceRepository.saveAndFlush(projection.balance());
        String source = projection.policy() == null ? "No entitlement configured" : "Entitlement policy effective " + projection.policy().getEffectiveFrom();
        transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), LeaveTxKind.ENTITLEMENT, balance.getEntitledDays(), null, source, actorId));
        if (balance.getCarriedOverDays().signum() > 0) {
            transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), LeaveTxKind.CARRY_OVER, balance.getCarriedOverDays(), null,
                    "Carried over from " + (year - 1), actorId));
        }
        return balanceRepository.lockById(balance.getId()).orElseThrow();
    }

    private static BigDecimal available(LeaveBalanceEntity b, LocalDate asOf) {
        BigDecimal earned = LeaveCalculator.earned(b.getEntitledDays(), b.getAccrual(), b.getAccrualStart(), b.getLeaveYear(), asOf);
        return LeaveCalculator.available(earned, b.getCarriedOverDays(), b.getAdjustmentDays(), b.getUsedDays(), b.getPendingDays());
    }

    // ================================================================ quotes and requests

    @Transactional(readOnly = true)
    public Quote quote(UserPrincipal principal, QuoteRequest r) {
        EmployeeEntity employee;
        if (r.employeeId() == null) {
            employee = me(principal);
        } else {
            HrAccess access = accessService.requireAny(principal, HrPermission.LEAVE_APPROVE, HrPermission.LEAVE_MANAGE);
            employee = employeeService.loadVisible(r.employeeId(), access);
        }
        LeaveTypeEntity type = setup.loadType(r.leaveTypeId());
        checkDates(r.startDate(), r.endDate());
        BigDecimal days = countDays(employee, type, r.startDate(), r.endDate(), r.startHalf(), r.endHalf());
        BigDecimal available = null;
        if (type.isDeductsBalance()) {
            LeaveBalanceEntity balance = balanceRepository.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employee.getId(), type.getId(), r.startDate().getYear())
                    .orElseGet(() -> project(employee, type, r.startDate().getYear()).balance());
            available = available(balance, r.startDate());
        }
        List<String> problems = new ArrayList<>();
        if (days.signum() == 0) problems.add("The selected dates contain no working days");
        if (type.getMaxDaysPerRequest() != null && days.compareTo(type.getMaxDaysPerRequest()) > 0) {
            problems.add("At most " + type.getMaxDaysPerRequest().stripTrailingZeros().toPlainString() + " days per request");
        }
        if (available != null && days.compareTo(available) > 0 && !type.isAllowNegative()) {
            problems.add("Only " + available.stripTrailingZeros().toPlainString() + " days available");
        }
        if (!requestRepository.findOverlapping(employee.getId(), ACTIVE, r.startDate(), r.endDate()).isEmpty()) {
            problems.add("Overlaps another leave request");
        }
        return new Quote(days, available, type.isDeductsBalance(), type.isRequiresDocument(), problems);
    }

    @Transactional
    public RequestView requestMine(UserPrincipal principal, RequestInput r) {
        HrAccess access = accessService.access(principal);
        EmployeeEntity employee = me(principal);
        return detail(create(access, employee, r, false, true), access);
    }

    /** A manager records leave for an employee (for example a sick call). With {@code recordApproved}, HR records it as already approved. */
    @Transactional
    public RequestView requestFor(UserPrincipal principal, UUID employeeId, RequestInput r, boolean recordApproved) {
        HrAccess access = accessService.requireAny(principal, HrPermission.LEAVE_APPROVE, HrPermission.LEAVE_MANAGE);
        EmployeeEntity employee = employeeService.loadInScope(employeeId, access);
        if (employee.getUserId() != null && employee.getUserId().equals(access.userId())) {
            throw new ApiException(400, "Use My leave to request your own leave");
        }
        if (recordApproved && !access.has(HrPermission.LEAVE_MANAGE)) {
            throw new ApiException(403, "Only HR can record leave as already approved");
        }
        LeaveRequestEntity request = create(access, employee, r, true, !recordApproved);
        if (recordApproved) {
            LeaveTypeEntity type = setup.loadType(request.getLeaveTypeId());
            if (type.isRequiresDocument() && request.getDocumentId() == null) {
                throw new ApiException(409, type.getName() + " needs a supporting document; attach it and approve the request instead");
            }
            List<ApprovalStep> steps = request.steps();
            for (int i = 0; i < steps.size(); i++) {
                saveApproval(request, i, steps.get(i), access.userId(), "APPROVED", i == 0 ? "Recorded as approved by HR" : null);
            }
            approveFinal(request, type, employee, access.userId());
        }
        return detail(request, access);
    }

    private LeaveRequestEntity create(HrAccess access, EmployeeEntity employee, RequestInput r, boolean onBehalf, boolean notifyApprovers) {
        LeaveTypeEntity type = setup.loadType(r.leaveTypeId());
        if (!type.isActive()) {
            throw new ApiException(400, type.getName() + " is no longer offered");
        }
        if (employee.getEmploymentStatus().isExit()) {
            throw new ApiException(400, "This person no longer works here");
        }
        checkDates(r.startDate(), r.endDate());
        if (employee.getHireDate() != null && r.startDate().isBefore(employee.getHireDate())) {
            throw new ApiException(400, "Leave cannot start before the hire date");
        }
        BigDecimal days = countDays(employee, type, r.startDate(), r.endDate(), r.startHalf(), r.endHalf());
        if (days.signum() == 0) {
            throw new ApiException(400, "The selected dates contain no working days");
        }
        if (type.getMaxDaysPerRequest() != null && days.compareTo(type.getMaxDaysPerRequest()) > 0) {
            throw new ApiException(400, "At most " + type.getMaxDaysPerRequest().stripTrailingZeros().toPlainString() + " days of "
                    + type.getName().toLowerCase() + " per request");
        }
        if (!requestRepository.findOverlapping(employee.getId(), ACTIVE, r.startDate(), r.endDate()).isEmpty()) {
            throw new ApiException(409, "This overlaps another leave request");
        }
        int year = r.startDate().getYear();
        setup.resolvePolicy(type.getId(), employee, year).ifPresent(p -> {
            if (p.getMinServiceDays() > 0 && employee.getHireDate() != null) {
                LocalDate eligible = employee.getHireDate().plusDays(p.getMinServiceDays());
                if (r.startDate().isBefore(eligible)) {
                    throw new ApiException(409, type.getName() + " can be taken from " + eligible);
                }
            }
        });
        if (r.documentId() != null) {
            documentService.assertBelongsTo(r.documentId(), employee.getId());
        }

        LeaveRequestEntity request = new LeaveRequestEntity();
        request.setEmployeeId(employee.getId());
        request.setLeaveTypeId(type.getId());
        request.setBranchId(employee.primaryBranchId());
        request.setStartDate(r.startDate());
        request.setEndDate(r.endDate());
        request.setStartHalf(r.startHalf());
        request.setEndHalf(r.endHalf());
        request.setDays(days);
        request.setReason(blankToNull(r.reason()));
        request.setDocumentId(r.documentId());
        request.setApprovalSteps(LeaveTypeEntity.formatSteps(resolveSteps(type, employee)));
        request.setRequestedBy(access.userId());
        requestRepository.saveAndFlush(request);

        if (type.isDeductsBalance()) {
            LeaveBalanceEntity balance = lockedBalance(employee, type, year, access.userId());
            BigDecimal available = available(balance, r.startDate());
            if (days.compareTo(available) > 0 && !type.isAllowNegative()) {
                throw new ApiException(409, "Only " + available.stripTrailingZeros().toPlainString() + " days of "
                        + type.getName().toLowerCase() + " are available");
            }
            balance.setPendingDays(balance.getPendingDays().add(days));
            transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), LeaveTxKind.PENDING, days, request.getId(), null, access.userId()));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", request.getId().toString());
        meta.put("leaveType", type.getCode());
        meta.put("from", r.startDate().toString());
        meta.put("to", r.endDate().toString());
        meta.put("days", days.toPlainString());
        meta.put("onBehalf", onBehalf);
        audit.log(access.userId(), AuditAction.HR_LEAVE_REQUESTED, "Employee", employee.getId(), meta);
        if (notifyApprovers) {
            notifier.awaitingDecision(request.pendingStep(), employee, request.getBranchId(), describe(type, request));
        }
        return request;
    }

    /** The type's chain for this employee; a reporting-manager step is dropped when there is no reporting manager with a login. */
    static List<ApprovalStep> resolveSteps(LeaveTypeEntity type, EmployeeEntity employee) {
        boolean hasManager = employee.getReportingManager() != null && employee.getReportingManager().getUserId() != null;
        List<ApprovalStep> steps = type.steps().stream().filter(s -> s != ApprovalStep.REPORTING_MANAGER || hasManager).toList();
        return steps.isEmpty() ? List.of(ApprovalStep.BRANCH_MANAGER) : steps;
    }

    private BigDecimal countDays(EmployeeEntity employee, LeaveTypeEntity type, LocalDate start, LocalDate end, boolean startHalf, boolean endHalf) {
        Set<LocalDate> holidays = type.getCountBasis() == CountBasis.WORKDAYS
                ? overtimeSettings.observedHolidays(employee.primaryBranchId(), start, end) : Set.of();
        return LeaveCalculator.countDays(start, end, startHalf, endHalf, type.getCountBasis(), setup.nonWorkingDays(), holidays);
    }

    private static void checkDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new ApiException(400, "The end date is before the start date");
        }
        if (start.getYear() != end.getYear()) {
            throw new ApiException(400, "Leave that continues into a new year must be requested as two requests, one per year");
        }
    }

    // ================================================================ decisions

    @Transactional
    public RequestView decide(UserPrincipal principal, UUID id, Decision decision) {
        HrAccess access = accessService.access(principal);
        LeaveRequestEntity request = requestRepository.findById(id).orElseThrow(() -> new ApiException(404, "Leave request not found"));
        EmployeeEntity employee = employeeRepository.findDetailedById(request.getEmployeeId()).orElseThrow();
        String refusal = refusal(access, request, employee);
        if (refusal != null && !canSee(access, request, employee)) {
            throw new ApiException(404, "Leave request not found");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(409, "This request has already been " + request.getStatus().name().toLowerCase());
        }
        if (refusal != null) {
            throw new ApiException(403, refusal);
        }
        String note = blankToNull(decision.note());
        if (!decision.approve() && note == null) {
            throw new ApiException(400, "Give a reason when declining leave");
        }
        LeaveTypeEntity type = setup.loadType(request.getLeaveTypeId());
        ApprovalStep step = request.pendingStep();
        int stepIndex = request.getCurrentStep();
        boolean last = stepIndex == request.steps().size() - 1;
        if (decision.approve() && last && type.isRequiresDocument() && request.getDocumentId() == null) {
            throw new ApiException(409, type.getName() + " needs a supporting document before it can be approved");
        }
        saveApproval(request, stepIndex, step, access.userId(), decision.approve() ? "APPROVED" : "REJECTED", note);
        if (!decision.approve()) {
            request.setStatus(LeaveStatus.REJECTED);
            request.setDecidedAt(Instant.now());
            moveBalance(request, type, access.userId(), LeaveTxKind.RELEASE, "Declined");
            notifier.decided(employee, false, describe(type, request));
        } else if (last) {
            approveFinal(request, type, employee, access.userId());
        } else {
            request.setCurrentStep(stepIndex + 1);
            notifier.awaitingDecision(request.pendingStep(), employee, request.getBranchId(), describe(type, request));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", id.toString());
        meta.put("step", step.name());
        meta.put("decision", decision.approve() ? "approved" : "rejected");
        meta.put("status", request.getStatus().name());
        audit.log(access.userId(), AuditAction.HR_LEAVE_DECIDED, "Employee", employee.getId(), meta);
        requestRepository.saveAndFlush(request);
        return detail(request, access);
    }

    private void approveFinal(LeaveRequestEntity request, LeaveTypeEntity type, EmployeeEntity employee, UUID actorId) {
        request.setStatus(LeaveStatus.APPROVED);
        request.setCurrentStep(request.steps().size());
        request.setDecidedAt(Instant.now());
        if (type.isDeductsBalance()) {
            LeaveBalanceEntity balance = lockedBalance(employee, type, request.getStartDate().getYear(), actorId);
            balance.setPendingDays(balance.getPendingDays().subtract(request.getDays()));
            balance.setUsedDays(balance.getUsedDays().add(request.getDays()));
            transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), LeaveTxKind.RELEASE, request.getDays().negate(), request.getId(), "Approved", actorId));
            transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), LeaveTxKind.USE, request.getDays(), request.getId(), null, actorId));
        }
        notifier.decided(employee, true, describe(type, request));
    }

    private void saveApproval(LeaveRequestEntity request, int index, ApprovalStep step, UUID actorId, String decision, String note) {
        LeaveApprovalEntity approval = new LeaveApprovalEntity();
        approval.setRequestId(request.getId());
        approval.setStep(index);
        approval.setStepCode(step.name());
        approval.setDecidedBy(actorId);
        approval.setDecision(decision);
        approval.setNote(note);
        approvalRepository.save(approval);
    }

    /**
     * Why {@code access} may not decide the request's current step, or null when they may. Nobody decides their own leave
     * or a request they filed, and each step of a chain needs a different person.
     */
    String refusal(HrAccess access, LeaveRequestEntity request, EmployeeEntity employee) {
        return refusal(access, request, employee, approvalRepository.findByRequestIdOrderByStepAsc(request.getId()));
    }

    private static String refusal(HrAccess access, LeaveRequestEntity request, EmployeeEntity employee, List<LeaveApprovalEntity> approvals) {
        ApprovalStep step = request.pendingStep();
        if (step == null) {
            return "This request is not waiting for a decision";
        }
        if (access.userId().equals(employee.getUserId())) {
            return "You cannot decide your own leave";
        }
        if (access.userId().equals(request.getRequestedBy())) {
            return "Someone else must decide a request you filed";
        }
        boolean decidedEarlier = approvals.stream()
                .anyMatch(a -> access.userId().equals(a.getDecidedBy()));
        if (decidedEarlier) {
            return "A different person must decide each approval step";
        }
        boolean allowed = switch (step) {
            case REPORTING_MANAGER -> (employee.getReportingManager() != null && access.userId().equals(employee.getReportingManager().getUserId()))
                    || (access.has(HrPermission.LEAVE_MANAGE) && access.canSeeBranch(request.getBranchId()));
            case BRANCH_MANAGER -> access.has(HrPermission.LEAVE_APPROVE) && access.canSeeBranch(request.getBranchId());
            case HR -> access.has(HrPermission.LEAVE_MANAGE) && access.canSeeBranch(request.getBranchId());
        };
        return allowed ? null : "This step waits for " + label(step);
    }

    private static boolean canSee(HrAccess access, LeaveRequestEntity request, EmployeeEntity employee) {
        if (access.userId().equals(employee.getUserId())) {
            return true;
        }
        if (employee.getReportingManager() != null && access.userId().equals(employee.getReportingManager().getUserId())) {
            return true;
        }
        return (access.has(HrPermission.LEAVE_APPROVE) || access.has(HrPermission.LEAVE_MANAGE)) && access.canSeeBranch(request.getBranchId());
    }

    static String label(ApprovalStep step) {
        return switch (step) {
            case REPORTING_MANAGER -> "the reporting manager";
            case BRANCH_MANAGER -> "a branch manager";
            case HR -> "HR";
        };
    }

    // ================================================================ cancellation and documents

    @Transactional
    public RequestView cancelMine(UserPrincipal principal, UUID id, CancelRequest r) {
        HrAccess access = accessService.access(principal);
        EmployeeEntity me = me(principal);
        LeaveRequestEntity request = requestRepository.findById(id)
                .filter(x -> x.getEmployeeId().equals(me.getId()))
                .orElseThrow(() -> new ApiException(404, "Leave request not found"));
        if (request.getStatus() == LeaveStatus.APPROVED && !request.getStartDate().isAfter(today())) {
            throw new ApiException(409, "Leave that has started can only be cancelled by a manager");
        }
        cancel(access, request, me, r == null ? null : r.reason(), false);
        return detail(request, access);
    }

    @Transactional
    public RequestView cancelFor(UserPrincipal principal, UUID id, CancelRequest r) {
        HrAccess access = accessService.requireAny(principal, HrPermission.LEAVE_APPROVE, HrPermission.LEAVE_MANAGE);
        LeaveRequestEntity request = requestRepository.findById(id).orElseThrow(() -> new ApiException(404, "Leave request not found"));
        if (!access.canSeeBranch(request.getBranchId())) {
            throw new ApiException(404, "Leave request not found");
        }
        EmployeeEntity employee = employeeRepository.findDetailedById(request.getEmployeeId()).orElseThrow();
        if (access.userId().equals(employee.getUserId())) {
            throw new ApiException(400, "Use My leave to cancel your own leave");
        }
        if (r == null || blankToNull(r.reason()) == null) {
            throw new ApiException(400, "Give a reason for cancelling");
        }
        cancel(access, request, employee, r.reason(), true);
        return detail(request, access);
    }

    private void cancel(HrAccess access, LeaveRequestEntity request, EmployeeEntity employee, String reason, boolean byManager) {
        if (!ACTIVE.contains(request.getStatus())) {
            throw new ApiException(409, "This request is already " + request.getStatus().name().toLowerCase());
        }
        LeaveTypeEntity type = setup.loadType(request.getLeaveTypeId());
        LeaveStatus before = request.getStatus();
        if (before == LeaveStatus.PENDING) {
            moveBalance(request, type, access.userId(), LeaveTxKind.RELEASE, "Cancelled");
        } else {
            moveBalance(request, type, access.userId(), LeaveTxKind.RESTORE, "Cancelled after approval");
        }
        request.setStatus(LeaveStatus.CANCELLED);
        request.setCancelReason(blankToNull(reason));
        requestRepository.saveAndFlush(request);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", request.getId().toString());
        meta.put("previousStatus", before.name());
        meta.put("byManager", byManager);
        audit.log(access.userId(), AuditAction.HR_LEAVE_CANCELLED, "Employee", employee.getId(), meta);
        if (byManager) {
            notifier.cancelledByManager(employee, describe(type, request));
        }
    }

    /** RELEASE frees pending days; RESTORE gives back used days. */
    private void moveBalance(LeaveRequestEntity request, LeaveTypeEntity type, UUID actorId, LeaveTxKind kind, String note) {
        if (!type.isDeductsBalance()) {
            return;
        }
        EmployeeEntity employee = employeeRepository.findById(request.getEmployeeId()).orElseThrow();
        LeaveBalanceEntity balance = lockedBalance(employee, type, request.getStartDate().getYear(), actorId);
        if (kind == LeaveTxKind.RELEASE) {
            balance.setPendingDays(balance.getPendingDays().subtract(request.getDays()));
        } else {
            balance.setUsedDays(balance.getUsedDays().subtract(request.getDays()));
        }
        transactionRepository.save(LeaveTransactionEntity.of(balance.getId(), kind, request.getDays().negate(), request.getId(), note, actorId));
    }

    @Transactional
    public RequestView attachDocument(UserPrincipal principal, UUID id, byte[] content, String fileName, boolean self) {
        HrAccess access = accessService.access(principal);
        LeaveRequestEntity request = requestRepository.findById(id).orElseThrow(() -> new ApiException(404, "Leave request not found"));
        EmployeeEntity employee = employeeRepository.findDetailedById(request.getEmployeeId()).orElseThrow();
        boolean own = access.userId().equals(employee.getUserId());
        boolean manager = (access.has(HrPermission.LEAVE_APPROVE) || access.has(HrPermission.LEAVE_MANAGE)) && access.canSeeBranch(request.getBranchId());
        if (self ? !own : !manager) {
            throw new ApiException(404, "Leave request not found");
        }
        if (!ACTIVE.contains(request.getStatus())) {
            throw new ApiException(409, "Documents can only be added to pending or approved leave");
        }
        LeaveTypeEntity type = setup.loadType(request.getLeaveTypeId());
        String title = type.getName() + " " + request.getStartDate() + (request.getEndDate().equals(request.getStartDate()) ? "" : " to " + request.getEndDate());
        HrDocumentService.DocumentView document = documentService.store(employee.getId(), access.userId(),
                new HrDocumentService.UploadRequest(content, fileName, DocumentType.LEAVE_SUPPORT, title, null, "Supporting document for leave request"));
        request.setDocumentId(document.id());
        requestRepository.saveAndFlush(request);
        return detail(request, access);
    }

    // ================================================================ queries

    @Transactional(readOnly = true)
    public List<RequestView> list(UserPrincipal principal, LeaveStatus status, UUID branchId, LocalDate from, LocalDate to, boolean awaitingMe,
                                  UUID employeeId) {
        HrAccess access = accessService.access(principal);
        List<LeaveRequestEntity> rows;
        if (employeeId != null) {
            if (!access.has(HrPermission.LEAVE_APPROVE) && !access.has(HrPermission.LEAVE_MANAGE)) {
                throw new ApiException(403, "You do not have permission to do this");
            }
            employeeService.loadVisible(employeeId, access);
            rows = requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                    .filter(r -> status == null || r.getStatus() == status).toList();
        } else if (awaitingMe) {
            rows = requestRepository.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING);
        } else {
            if (!access.has(HrPermission.LEAVE_APPROVE) && !access.has(HrPermission.LEAVE_MANAGE)) {
                throw new ApiException(403, "You do not have permission to do this");
            }
            // default: recent and all upcoming leave (people book holidays far ahead)
            LocalDate start = from == null ? today().minusMonths(1) : from;
            LocalDate end = to == null ? start.plusMonths(13) : to;
            if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 400) {
                throw new ApiException(400, "Choose a date range of at most 400 days");
            }
            Set<LeaveStatus> statuses = status == null ? EnumSet.allOf(LeaveStatus.class) : EnumSet.of(status);
            if (branchId != null) {
                access.assertBranch(branchId);
                rows = requestRepository.findAtBranches(List.of(branchId), statuses, start, end);
            } else if (access.allBranches()) {
                rows = requestRepository.findAllBranches(statuses, start, end);
            } else if (access.branchIds().isEmpty()) {
                rows = List.of();
            } else {
                rows = requestRepository.findAtBranches(access.branchIds(), statuses, start, end);
            }
        }
        Map<UUID, EmployeeEntity> employees = employees(rows);
        Map<UUID, List<LeaveApprovalEntity>> approvals = awaitingMe ? approvalsOf(rows) : Map.of();
        List<LeaveRequestEntity> visible = rows.stream()
                .filter(r -> awaitingMe ? refusal(access, r, employees.get(r.getEmployeeId()), approvals.getOrDefault(r.getId(), List.of())) == null
                        : canSee(access, r, employees.get(r.getEmployeeId())))
                .toList();
        return views(visible, employees, access);
    }

    @Transactional(readOnly = true)
    public List<RequestView> mine(UserPrincipal principal) {
        HrAccess access = accessService.access(principal);
        EmployeeEntity me = me(principal);
        List<LeaveRequestEntity> rows = requestRepository.findByEmployeeIdOrderByStartDateDesc(me.getId());
        return views(rows, Map.of(me.getId(), me), access);
    }

    @Transactional(readOnly = true)
    public RequestView get(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.access(principal);
        LeaveRequestEntity request = requestRepository.findById(id).orElseThrow(() -> new ApiException(404, "Leave request not found"));
        EmployeeEntity employee = employeeRepository.findDetailedById(request.getEmployeeId()).orElseThrow();
        if (!canSee(access, request, employee)) {
            throw new ApiException(404, "Leave request not found");
        }
        return detail(request, access);
    }

    /** Pending and approved leave at a branch, for planning rotas. */
    @Transactional(readOnly = true)
    public List<RequestView> calendar(UserPrincipal principal, UUID branchId, LocalDate from, LocalDate to) {
        HrAccess access = accessService.requireAny(principal, HrPermission.LEAVE_APPROVE, HrPermission.LEAVE_MANAGE, HrPermission.SCHEDULE_VIEW);
        access.assertBranch(branchId);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_CALENDAR_DAYS) {
            throw new ApiException(400, "Choose a date range of at most " + MAX_CALENDAR_DAYS + " days");
        }
        List<LeaveRequestEntity> rows = requestRepository.findAtBranches(List.of(branchId), ACTIVE, from, to);
        return views(rows, employees(rows), access);
    }

    private RequestView detail(LeaveRequestEntity request, HrAccess access) {
        EmployeeEntity employee = employeeRepository.findDetailedById(request.getEmployeeId()).orElseThrow();
        RequestView view = views(List.of(request), Map.of(employee.getId(), employee), access).get(0);
        List<ShiftConflict> conflicts = List.of();
        if (ACTIVE.contains(request.getStatus())) {
            Instant from = request.getStartDate().atStartOfDay(zone).toInstant();
            Instant to = request.getEndDate().plusDays(1).atStartOfDay(zone).toInstant();
            conflicts = scheduleRepository.findActiveForEmployee(from, to, employee.getId()).stream()
                    .filter(s -> s.getStatus() != ScheduleStatus.CANCELLED)
                    .map(s -> new ShiftConflict(s.getId(), s.getBranchId(), s.getStartsAt(), s.getEndsAt(), s.getStatus().name()))
                    .toList();
        }
        return view.withConflicts(conflicts);
    }

    private List<RequestView> views(List<LeaveRequestEntity> rows, Map<UUID, EmployeeEntity> employees, HrAccess access) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, LeaveTypeEntity> types = new HashMap<>();
        typeRepository.findAll().forEach(t -> types.put(t.getId(), t));
        Map<UUID, List<LeaveApprovalEntity>> approvals = approvalsOf(rows);
        Map<UUID, String> names = userNames(approvals.values().stream().flatMap(List::stream).map(LeaveApprovalEntity::getDecidedBy).toList());
        List<RequestView> views = new ArrayList<>();
        for (LeaveRequestEntity r : rows) {
            EmployeeEntity e = employees.get(r.getEmployeeId());
            LeaveTypeEntity t = types.get(r.getLeaveTypeId());
            Map<Integer, LeaveApprovalEntity> byStep = approvals.getOrDefault(r.getId(), List.of()).stream()
                    .collect(Collectors.toMap(LeaveApprovalEntity::getStep, Function.identity()));
            List<ApprovalStep> steps = r.steps();
            List<StepView> stepViews = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                LeaveApprovalEntity a = byStep.get(i);
                stepViews.add(a == null ? new StepView(i, steps.get(i), null, null, null, null)
                        : new StepView(i, steps.get(i), a.getDecision(), names.get(a.getDecidedBy()), a.getNote(), a.getDecidedAt()));
            }
            boolean canDecide = e != null && refusal(access, r, e, approvals.getOrDefault(r.getId(), List.of())) == null;
            views.add(new RequestView(r.getId(), r.getEmployeeId(), e == null ? null : e.fullName(), r.getBranchId(),
                    r.getLeaveTypeId(), t == null ? null : t.getCode(), t == null ? null : t.getName(), t != null && t.isPaid(),
                    r.getStartDate(), r.getEndDate(), r.isStartHalf(), r.isEndHalf(), r.getDays(), r.getReason(), r.getStatus(),
                    stepViews, r.pendingStep(), canDecide, t != null && t.isRequiresDocument(), r.getDocumentId(), r.getCancelReason(),
                    r.getDecidedAt(), r.getCreatedAt(), null));
        }
        return views;
    }

    private Map<UUID, List<LeaveApprovalEntity>> approvalsOf(Collection<LeaveRequestEntity> rows) {
        return approvalRepository.findByRequestIdIn(rows.stream().map(LeaveRequestEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(LeaveApprovalEntity::getRequestId));
    }

    private Map<UUID, EmployeeEntity> employees(Collection<LeaveRequestEntity> rows) {
        Map<UUID, EmployeeEntity> map = new HashMap<>();
        employeeRepository.findByIdIn(rows.stream().map(LeaveRequestEntity::getEmployeeId).distinct().toList()).forEach(e -> map.put(e.getId(), e));
        return map;
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            userRepository.findAllById(distinct).forEach(u -> names.put(u.getId(), u.getName()));
        }
        return names;
    }

    private EmployeeEntity me(UserPrincipal principal) {
        UserEntity user = userRepository.findById(principal.userId()).orElseThrow(() -> new ApiException(401, "Unauthorized"));
        return employeeRepository.findByUserId(user.getId())
                .flatMap(e -> employeeRepository.findDetailedById(e.getId()))
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
    }

    private static String describe(LeaveTypeEntity type, LeaveRequestEntity r) {
        return type.getName().toLowerCase() + " (" + r.getStartDate() + (r.getEndDate().equals(r.getStartDate()) ? "" : " to " + r.getEndDate()) + ")";
    }

    private static BalanceView toView(LeaveBalanceEntity b, LeaveTypeEntity type, LocalDate asOf) {
        BigDecimal earned = LeaveCalculator.earned(b.getEntitledDays(), b.getAccrual(), b.getAccrualStart(), b.getLeaveYear(), asOf);
        return new BalanceView(b.getId(), type.getId(), type.getCode(), type.getName(), b.getLeaveYear(), b.getEntitledDays(), b.getAccrual(),
                earned, b.getCarriedOverDays(), b.getAdjustmentDays(), b.getUsedDays(), b.getPendingDays(),
                LeaveCalculator.available(earned, b.getCarriedOverDays(), b.getAdjustmentDays(), b.getUsedDays(), b.getPendingDays()),
                type.isAllowNegative(), b.getPolicyId() != null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Projection(LeaveBalanceEntity balance, LeavePolicyEntity policy) {}

    // ================================================================ DTOs

    public record BalanceView(UUID id, UUID leaveTypeId, String leaveTypeCode, String leaveTypeName, int year, BigDecimal entitledDays,
                              Accrual accrual, BigDecimal earnedDays, BigDecimal carriedOverDays, BigDecimal adjustmentDays,
                              BigDecimal usedDays, BigDecimal pendingDays, BigDecimal availableDays, boolean allowNegative,
                              boolean hasPolicy) {}

    public record TransactionView(UUID id, LeaveTxKind kind, BigDecimal days, UUID requestId, String note, String actorName, Instant createdAt) {}

    public record AdjustRequest(@NotNull UUID leaveTypeId, int year,
                                @NotNull @DecimalMin("-366") @DecimalMax("366") BigDecimal days,
                                @NotBlank @Size(max = 300) String note) {}

    public record QuoteRequest(UUID employeeId, @NotNull UUID leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                               boolean startHalf, boolean endHalf) {}

    public record Quote(BigDecimal days, BigDecimal availableDays, boolean deductsBalance, boolean requiresDocument, List<String> problems) {}

    public record RequestInput(@NotNull UUID leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                               boolean startHalf, boolean endHalf, @Size(max = 500) String reason, UUID documentId) {}

    public record OnBehalfInput(@NotNull UUID employeeId, @NotNull UUID leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                boolean startHalf, boolean endHalf, @Size(max = 500) String reason, UUID documentId, boolean recordApproved) {
        RequestInput toInput() {
            return new RequestInput(leaveTypeId, startDate, endDate, startHalf, endHalf, reason, documentId);
        }
    }

    public record Decision(boolean approve, @Size(max = 500) String note) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record StepView(int step, ApprovalStep code, String decision, String decidedByName, String note, Instant decidedAt) {}

    public record ShiftConflict(UUID scheduleId, UUID branchId, Instant startsAt, Instant endsAt, String status) {}

    public record RequestView(UUID id, UUID employeeId, String employeeName, UUID branchId, UUID leaveTypeId, String leaveTypeCode,
                              String leaveTypeName, boolean paid, LocalDate startDate, LocalDate endDate, boolean startHalf, boolean endHalf,
                              BigDecimal days, String reason, LeaveStatus status, List<StepView> steps, ApprovalStep pendingStep,
                              boolean canDecide, boolean documentRequired, UUID documentId, String cancelReason, Instant decidedAt,
                              Instant createdAt, List<ShiftConflict> conflictingShifts) {
        RequestView withConflicts(List<ShiftConflict> conflicts) {
            return new RequestView(id, employeeId, employeeName, branchId, leaveTypeId, leaveTypeCode, leaveTypeName, paid, startDate, endDate,
                    startHalf, endHalf, days, reason, status, steps, pendingStep, canDecide, documentRequired, documentId, cancelReason,
                    decidedAt, createdAt, conflicts);
        }
    }
}
