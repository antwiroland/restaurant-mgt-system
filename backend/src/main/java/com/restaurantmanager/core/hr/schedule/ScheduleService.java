package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.leave.LeaveLookup;
import com.restaurantmanager.core.hr.org.PositionEntity;
import com.restaurantmanager.core.hr.org.PositionRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds, validates and publishes branch schedules on top of the {@code work_schedules} rota. */
@Service
public class ScheduleService {
    static final int MAX_RANGE_DAYS = 62;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);

    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;
    private final PositionRepository positionRepository;
    private final ShiftTemplateRepository templateRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SwapRequestRepository swapRepository;
    private final SchedulingSetupService setupService;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ScheduleNotifier notifier;
    private final LeaveLookup leaveLookup;
    private final LabourCostCalculator labourCost;
    private final ZoneId zone;

    public ScheduleService(WorkScheduleRepository scheduleRepository, EmployeeRepository employeeRepository,
                           BranchAssignmentRepository assignmentRepository, BranchRepository branchRepository,
                           PositionRepository positionRepository, ShiftTemplateRepository templateRepository,
                           AvailabilityRepository availabilityRepository, SwapRequestRepository swapRepository,
                           SchedulingSetupService setupService, HrAccessService accessService, HrAuditService audit,
                           ScheduleNotifier notifier, LeaveLookup leaveLookup, LabourCostCalculator labourCost,
                           @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.labourCost = labourCost;
        this.scheduleRepository = scheduleRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.branchRepository = branchRepository;
        this.positionRepository = positionRepository;
        this.templateRepository = templateRepository;
        this.availabilityRepository = availabilityRepository;
        this.swapRepository = swapRepository;
        this.setupService = setupService;
        this.accessService = accessService;
        this.audit = audit;
        this.notifier = notifier;
        this.leaveLookup = leaveLookup;
        this.zone = ZoneId.of(timeZone);
    }

    public ZoneId zone() {
        return zone;
    }

    // ------------------------------------------------------------ board

    @Transactional(readOnly = true)
    public BoardView board(UserPrincipal principal, UUID branchId, LocalDate from, LocalDate to) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_VIEW);
        access.assertBranch(branchId);
        checkRange(from, to);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<WorkScheduleEntity> shifts = scheduleRepository.findAtBranches(start, end, List.of(branchId));

        Set<UUID> employeeIds = new HashSet<>(assignmentRepository.findEmployeeIdsAtBranch(branchId, from, to));
        shifts.stream().map(WorkScheduleEntity::getEmployeeId).filter(Objects::nonNull).forEach(employeeIds::add);
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(employeeIds).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        Set<UUID> withShifts = shifts.stream().map(WorkScheduleEntity::getEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<BoardEmployee> rows = employees.values().stream()
                .filter(e -> !e.getEmploymentStatus().isExit() || withShifts.contains(e.getId()))
                .sorted(Comparator.comparing((EmployeeEntity e) -> e.getPosition() == null ? "~" : e.getPosition().getTitle())
                        .thenComparing(EmployeeEntity::fullName))
                .map(e -> new BoardEmployee(e.getId(), e.fullName(), e.getPosition() == null ? null : e.getPosition().getTitle(),
                        e.getEmploymentStatus().name(), branchId.equals(e.primaryBranchId())))
                .toList();

        List<CoverageCell> coverage = coverage(branchId, from, to, shifts);
        // The total is shown to schedulers; per-person figures only to those who may see pay.
        LabourCostCalculator.LabourCost labour = labourCost.estimate(shifts, employees, access.has(HrPermission.COMPENSATION_VIEW));
        return new BoardView(branchId, from, to, toViews(shifts, employees), rows, coverage, setupService.effectivePolicy(branchId), labour);
    }

    /** For each active template at the branch and each matching day: assigned vs required staff, plus open shifts. */
    List<CoverageCell> coverage(UUID branchId, LocalDate from, LocalDate to, List<WorkScheduleEntity> shifts) {
        List<ShiftTemplateEntity> templates = templateRepository.findAllByOrderByStartTimeAscNameAsc().stream()
                .filter(ShiftTemplateEntity::isActive)
                .filter(t -> t.getBranchId() == null || t.getBranchId().equals(branchId))
                .toList();
        Map<String, List<WorkScheduleEntity>> byTemplateDay = shifts.stream()
                .filter(s -> s.getStatus() != ScheduleStatus.CANCELLED && s.getTemplateId() != null)
                .collect(Collectors.groupingBy(s -> s.getTemplateId() + "|" + s.getStartsAt().atZone(zone).toLocalDate()));
        List<CoverageCell> cells = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            for (ShiftTemplateEntity t : templates) {
                if (!t.days().contains(day.getDayOfWeek())) {
                    continue;
                }
                List<WorkScheduleEntity> matching = byTemplateDay.getOrDefault(t.getId() + "|" + day, List.of());
                long assigned = matching.stream().filter(s -> s.getEmployeeId() != null).count();
                long open = matching.size() - assigned;
                String state = assigned < t.getMinStaff() ? "UNDERSTAFFED"
                        : t.getMaxStaff() != null && assigned > t.getMaxStaff() ? "OVERSTAFFED" : "OK";
                cells.add(new CoverageCell(day, t.getId(), t.getName(), t.getMinStaff(), t.getMaxStaff(), assigned, open, state));
            }
        }
        return cells;
    }

    // ------------------------------------------------------------ save / cancel / delete

    @Transactional
    public SaveResult save(UserPrincipal principal, UUID id, ShiftRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        WorkScheduleEntity shift = id == null ? new WorkScheduleEntity() : loadInScope(id, access);
        if (id != null && shift.getStatus() == ScheduleStatus.CANCELLED) {
            throw new ApiException(409, "Cancelled shifts cannot be edited");
        }
        if (id != null && request.version() != null && request.version() != shift.getVersion()) {
            throw new ApiException(409, "This shift was changed by someone else. Reload and try again.");
        }
        access.assertBranch(request.branchId());
        BranchEntity branch = branchRepository.findById(request.branchId()).orElseThrow(() -> new ApiException(404, "Branch not found"));
        if (!branch.isActive()) {
            throw new ApiException(400, "Branch " + branch.getName() + " is inactive");
        }

        ShiftTemplateEntity template = request.templateId() == null ? null : setupService.loadTemplate(request.templateId(), access);
        if (template != null && template.getBranchId() != null && !template.getBranchId().equals(branch.getId())) {
            throw new ApiException(400, "This template belongs to another branch");
        }
        Instant startsAt;
        Instant endsAt;
        if (request.startsAt() != null && request.endsAt() != null) {
            startsAt = request.startsAt();
            endsAt = request.endsAt();
        } else if (template != null && request.date() != null) {
            startsAt = template.startOn(request.date(), zone);
            endsAt = template.endOn(request.date(), zone);
        } else {
            throw new ApiException(400, "Give start and end times, or a template and a date");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ApiException(400, "Shift must end after it starts");
        }
        if (Duration.between(startsAt, endsAt).toHours() > 24) {
            throw new ApiException(400, "A shift cannot be longer than 24 hours");
        }
        int breakMinutes = request.breakMinutes() != null ? request.breakMinutes() : template != null ? template.getBreakMinutes() : 0;
        if (breakMinutes >= Duration.between(startsAt, endsAt).toMinutes()) {
            throw new ApiException(400, "The break is longer than the shift");
        }

        EmployeeEntity employee = request.employeeId() == null ? null
                : employeeRepository.findById(request.employeeId()).orElseThrow(() -> new ApiException(404, "Employee not found"));
        List<ScheduleRules.Issue> issues = employee == null ? List.of()
                : evaluate(employee, branch.getId(), new ScheduleRules.Interval(startsAt, endsAt, breakMinutes), Set.of(id == null ? new UUID(0, 0) : id));
        enforce(issues, request.acknowledgeWarnings());

        UUID previousUser = shift.getUserId();
        boolean wasPublished = shift.getStatus() == ScheduleStatus.PUBLISHED && id != null;
        String previousWhen = id == null ? null : when(shift.getStartsAt());
        shift.setEmployeeId(employee == null ? null : employee.getId());
        shift.setUserId(employee == null ? null : employee.getUserId());
        shift.setBranchId(branch.getId());
        shift.setStartsAt(startsAt);
        shift.setEndsAt(endsAt);
        shift.setBreakMinutes(breakMinutes);
        shift.setTemplateId(template == null ? null : template.getId());
        UUID positionId = request.positionId() != null ? request.positionId()
                : template != null && template.getPositionId() != null ? template.getPositionId()
                : employee != null && employee.getPosition() != null ? employee.getPosition().getId() : null;
        PositionEntity position = positionId == null ? null : positionRepository.findById(positionId).orElseThrow(() -> new ApiException(404, "Position not found"));
        shift.setPositionId(positionId);
        shift.setPosition(position == null ? null : truncate(position.getTitle(), 60));
        shift.setDepartmentId(request.departmentId() != null ? request.departmentId()
                : template != null && template.getDepartmentId() != null ? template.getDepartmentId()
                : position != null && position.getDepartment() != null ? position.getDepartment().getId() : null);
        shift.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        if (id == null) {
            shift.setStatus(ScheduleStatus.DRAFT);
            shift.setCreatedBy(access.userId());
        }
        WorkScheduleEntity saved = scheduleRepository.saveAndFlush(shift);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", id == null ? "created" : "updated");
        meta.put("status", saved.getStatus().name());
        meta.put("employeeId", String.valueOf(saved.getEmployeeId()));
        meta.put("startsAt", saved.getStartsAt().toString());
        meta.put("endsAt", saved.getEndsAt().toString());
        if (!issues.isEmpty()) {
            meta.put("acknowledgedWarnings", issues.stream().map(ScheduleRules.Issue::code).toList());
        }
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Schedule", saved.getId(), meta);
        if (wasPublished) {
            Set<UUID> notify = new HashSet<>();
            notify.add(saved.getUserId());
            notify.add(previousUser);
            notifier.shiftChanged(notify, previousWhen);
        }
        return new SaveResult(toViews(List.of(saved), employee == null ? Map.of() : Map.of(employee.getId(), employee)).get(0), issues);
    }

    @Transactional
    public ShiftView cancel(UserPrincipal principal, UUID id, CancelRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        WorkScheduleEntity shift = loadInScope(id, access);
        if (shift.getStatus() == ScheduleStatus.CANCELLED) {
            throw new ApiException(409, "This shift is already cancelled");
        }
        boolean wasPublished = shift.getStatus() == ScheduleStatus.PUBLISHED;
        if (wasPublished && (request.reason() == null || request.reason().isBlank())) {
            throw new ApiException(400, "A reason is required to cancel a published shift");
        }
        shift.setStatus(ScheduleStatus.CANCELLED);
        shift.setCancelledAt(Instant.now());
        shift.setCancelReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        swapRepository.findByStatusOrderByCreatedAtAsc(SwapStatus.PENDING).stream()
                .filter(s -> id.equals(s.getScheduleId()) || id.equals(s.getColleagueScheduleId()))
                .forEach(s -> {
                    s.setStatus(SwapStatus.CANCELLED);
                    s.setDecisionNote("Shift was cancelled");
                    s.setDecidedAt(Instant.now());
                });
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Schedule", id,
                Map.of("operation", "cancelled", "reason", String.valueOf(shift.getCancelReason())));
        if (wasPublished) {
            notifier.shiftCancelled(java.util.Collections.singleton(shift.getUserId()), when(shift.getStartsAt()));
        }
        return toViews(List.of(shift), employeeMap(List.of(shift))).get(0);
    }

    /** Drafts were never visible to staff, so they can be removed outright; published shifts must be cancelled. */
    @Transactional
    public void deleteDraft(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        WorkScheduleEntity shift = loadInScope(id, access);
        if (shift.getStatus() != ScheduleStatus.DRAFT) {
            throw new ApiException(409, "Only draft shifts can be deleted. Cancel published shifts instead.");
        }
        scheduleRepository.delete(shift);
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Schedule", id, Map.of("operation", "draft-deleted"));
    }

    /**
     * Publishes every draft at the branch in the date range. All drafts are re-checked first; if any has a blocking
     * problem (e.g. the employee left since it was drafted) nothing is published and the problems are returned.
     */
    @Transactional
    public PublishResult publish(UserPrincipal principal, PublishRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_PUBLISH);
        access.assertBranch(request.branchId());
        checkRange(request.from(), request.to());
        Instant start = request.from().atStartOfDay(zone).toInstant();
        Instant end = request.to().plusDays(1).atStartOfDay(zone).toInstant();
        List<WorkScheduleEntity> drafts = scheduleRepository.findDraftsAtBranch(start, end, request.branchId());
        if (drafts.isEmpty()) {
            return new PublishResult(0, 0, 0);
        }
        Map<UUID, EmployeeEntity> employees = employeeMap(drafts);
        List<ScheduleRules.Issue> problems = new ArrayList<>();
        for (WorkScheduleEntity draft : drafts) {
            EmployeeEntity employee = draft.getEmployeeId() == null ? null : employees.get(draft.getEmployeeId());
            if (employee == null) {
                continue;
            }
            evaluate(employee, draft.getBranchId(), interval(draft), Set.of(draft.getId())).stream()
                    .filter(i -> i.severity() == ScheduleRules.Severity.BLOCKING)
                    .forEach(i -> problems.add(new ScheduleRules.Issue(i.code(), i.severity(),
                            employee.fullName() + ", " + when(draft.getStartsAt()) + ": " + i.message())));
        }
        if (!problems.isEmpty()) {
            throw new ScheduleIssuesException(problems.size() + " draft shift(s) need attention before publishing", problems);
        }
        Instant now = Instant.now();
        Set<UUID> notify = new HashSet<>();
        for (WorkScheduleEntity draft : drafts) {
            draft.setStatus(ScheduleStatus.PUBLISHED);
            draft.setPublishedAt(now);
            draft.setPublishedBy(access.userId());
            EmployeeEntity employee = draft.getEmployeeId() == null ? null : employees.get(draft.getEmployeeId());
            if (employee != null) {
                draft.setUserId(employee.getUserId()); // the login may have been linked after drafting
                notify.add(employee.getUserId());
            }
        }
        long open = drafts.stream().filter(d -> d.getEmployeeId() == null).count();
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_PUBLISHED, "Branch", request.branchId(),
                Map.of("from", request.from().toString(), "to", request.to().toString(), "shifts", drafts.size()));
        notifier.schedulePublished(notify, request.from() + " to " + request.to());
        return new PublishResult(drafts.size(), open, (int) notify.stream().filter(Objects::nonNull).count());
    }

    // ------------------------------------------------------------ self-service

    @Transactional(readOnly = true)
    public List<ShiftView> myShifts(UserPrincipal principal, LocalDate from, LocalDate to) {
        checkRange(from, to);
        EmployeeEntity me = employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
        List<WorkScheduleEntity> shifts = scheduleRepository.findPublishedForEmployee(from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(), me.getId());
        return toViews(shifts, Map.of(me.getId(), me));
    }

    // ------------------------------------------------------------ shared

    /** Runs every rule for placing {@code employee} on {@code candidate} at {@code branchId}, ignoring the shifts in {@code exclude}. */
    public List<ScheduleRules.Issue> evaluate(EmployeeEntity employee, UUID branchId, ScheduleRules.Interval candidate, Set<UUID> exclude) {
        ZonedDateTime local = candidate.start().atZone(zone);
        Instant weekStart = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
        Instant windowStart = min(weekStart, candidate.start().minus(2, ChronoUnit.DAYS));
        Instant windowEnd = max(weekStart.plus(7, ChronoUnit.DAYS), candidate.end().plus(2, ChronoUnit.DAYS));
        List<ScheduleRules.Interval> others = scheduleRepository.findActiveForEmployee(windowStart, windowEnd, employee.getId()).stream()
                .filter(s -> !exclude.contains(s.getId()))
                .map(ScheduleService::interval)
                .toList();
        boolean assigned = assignmentRepository.existsActiveAt(employee.getId(), branchId, local.toLocalDate());
        List<ScheduleRules.LeavePeriod> leave = leaveLookup.periods(employee.getId(), local.toLocalDate(),
                candidate.end().minusSeconds(1).atZone(zone).toLocalDate());
        return ScheduleRules.evaluate(candidate, others, setupService.effectivePolicy(branchId), employee.getEmploymentStatus(),
                assigned, availabilityRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()), leave, zone);
    }

    static void enforce(List<ScheduleRules.Issue> issues, boolean acknowledged) {
        if (issues.stream().anyMatch(i -> i.severity() == ScheduleRules.Severity.BLOCKING)) {
            throw new ScheduleIssuesException("This shift cannot be scheduled", issues);
        }
        if (!issues.isEmpty() && !acknowledged) {
            throw new ScheduleIssuesException("Review the warnings and confirm to save", issues);
        }
    }

    public WorkScheduleEntity loadInScope(UUID id, HrAccess access) {
        WorkScheduleEntity shift = scheduleRepository.findById(id).orElseThrow(() -> new ApiException(404, "Shift not found"));
        if (!access.canSeeBranch(shift.getBranchId())) {
            throw new ApiException(404, "Shift not found");
        }
        return shift;
    }

    public static ScheduleRules.Interval interval(WorkScheduleEntity s) {
        return new ScheduleRules.Interval(s.getStartsAt(), s.getEndsAt(), s.getBreakMinutes());
    }

    public String when(Instant instant) {
        return WHEN.format(instant.atZone(zone));
    }

    Map<UUID, EmployeeEntity> employeeMap(List<WorkScheduleEntity> shifts) {
        List<UUID> ids = shifts.stream().map(WorkScheduleEntity::getEmployeeId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : employeeRepository.findByIdIn(ids).stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
    }

    public List<ShiftView> toViews(List<WorkScheduleEntity> shifts, Map<UUID, EmployeeEntity> employees) {
        Map<UUID, String> templateNames = new HashMap<>();
        List<UUID> templateIds = shifts.stream().map(WorkScheduleEntity::getTemplateId).filter(Objects::nonNull).distinct().toList();
        if (!templateIds.isEmpty()) {
            templateRepository.findAllById(templateIds).forEach(t -> templateNames.put(t.getId(), t.getName()));
        }
        return shifts.stream().map(s -> {
            EmployeeEntity e = s.getEmployeeId() == null ? null : employees.get(s.getEmployeeId());
            long minutes = Duration.between(s.getStartsAt(), s.getEndsAt()).toMinutes();
            return new ShiftView(s.getId(), s.getEmployeeId(), e == null ? null : e.fullName(), s.getBranchId(), s.getStartsAt(), s.getEndsAt(),
                    s.getBreakMinutes(), Math.max(0, minutes - s.getBreakMinutes()), s.getStatus(), s.getTemplateId(),
                    s.getTemplateId() == null ? null : templateNames.get(s.getTemplateId()), s.getDepartmentId(), s.getPositionId(),
                    s.getPosition(), s.getNotes(), s.getPublishedAt(), s.getCancelReason(), s.getVersion());
        }).toList();
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(400, "Choose a valid date range");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ApiException(400, "Choose a range of at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ------------------------------------------------------------ DTOs

    public record ShiftRequest(UUID employeeId, @NotNull UUID branchId, UUID templateId, LocalDate date,
                               Instant startsAt, Instant endsAt, @Min(0) @Max(480) Integer breakMinutes,
                               UUID departmentId, UUID positionId, @Size(max = 500) String notes,
                               boolean acknowledgeWarnings, Long version) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record PublishRequest(@NotNull UUID branchId, @NotNull LocalDate from, @NotNull LocalDate to) {}

    public record PublishResult(int published, long openShifts, int employeesNotified) {}

    public record ShiftView(UUID id, UUID employeeId, String employeeName, UUID branchId, Instant startsAt, Instant endsAt,
                            int breakMinutes, long paidMinutes, ScheduleStatus status, UUID templateId, String templateName,
                            UUID departmentId, UUID positionId, String positionTitle, String notes, Instant publishedAt,
                            String cancelReason, long version) {}

    public record SaveResult(ShiftView shift, List<ScheduleRules.Issue> acknowledgedWarnings) {}

    public record BoardEmployee(UUID id, String name, String positionTitle, String status, boolean homeBranch) {}

    public record CoverageCell(LocalDate date, UUID templateId, String templateName, int minStaff, Integer maxStaff,
                               long assigned, long open, String state) {}

    public record BoardView(UUID branchId, LocalDate from, LocalDate to, List<ShiftView> shifts, List<BoardEmployee> employees,
                            List<CoverageCell> coverage, ScheduleRules.Policy policy, LabourCostCalculator.LabourCost labour) {}
}
