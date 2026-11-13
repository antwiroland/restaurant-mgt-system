package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.branch.BranchAssignmentEntity;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scheduling helpers on top of {@link ScheduleService}: publishing a single shift, copying a week as drafts, and
 * open shifts staff can pick up themselves. Every placement runs the same rules as building a shift by hand.
 */
@Service
public class ScheduleExtrasService {
    /** Staff see open shifts this far ahead. */
    static final int OPEN_SHIFT_DAYS = 30;

    private final ScheduleService scheduleService;
    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ScheduleNotifier notifier;

    public ScheduleExtrasService(ScheduleService scheduleService, WorkScheduleRepository scheduleRepository,
                                 EmployeeRepository employeeRepository, BranchAssignmentRepository assignmentRepository,
                                 UserRepository userRepository, HrAccessService accessService, HrAuditService audit,
                                 ScheduleNotifier notifier) {
        this.scheduleService = scheduleService;
        this.scheduleRepository = scheduleRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.notifier = notifier;
    }

    /** Publishes one draft (re-checked first) and tells the employee. */
    @Transactional
    public ScheduleService.ShiftView publishShift(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_PUBLISH);
        WorkScheduleEntity shift = scheduleService.loadInScope(id, access);
        if (shift.getStatus() != ScheduleStatus.DRAFT) {
            throw new ApiException(409, "Only draft shifts can be published");
        }
        EmployeeEntity employee = shift.getEmployeeId() == null ? null : employeeRepository.findById(shift.getEmployeeId()).orElse(null);
        if (employee != null) {
            List<ScheduleRules.Issue> blocking = scheduleService.evaluate(employee, shift.getBranchId(), ScheduleService.interval(shift), Set.of(shift.getId()))
                    .stream().filter(i -> i.severity() == ScheduleRules.Severity.BLOCKING).toList();
            if (!blocking.isEmpty()) {
                throw new ScheduleIssuesException("This shift cannot be published", blocking);
            }
            shift.setUserId(employee.getUserId());
        }
        shift.setStatus(ScheduleStatus.PUBLISHED);
        shift.setPublishedAt(Instant.now());
        shift.setPublishedBy(access.userId());
        scheduleRepository.save(shift);
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_PUBLISHED, "Schedule", shift.getId(), Map.of("operation", "published-one"));
        if (shift.getUserId() != null) {
            notifier.schedulePublished(Set.of(shift.getUserId()), scheduleService.when(shift.getStartsAt()));
        }
        return views(List.of(shift)).get(0);
    }

    /**
     * Copies a week's shifts at a branch to another week as drafts (same weekday and times). Shifts that would break a
     * blocking rule in the new week (the person left, is on leave, already works then) are skipped and reported;
     * warnings are left for the scheduler to review before publishing.
     */
    @Transactional
    public CopyWeekResult copyWeek(UserPrincipal principal, CopyWeekRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        access.assertBranch(request.branchId());
        LocalDate fromMonday = request.fromWeek().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate toMonday = request.toWeek().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (fromMonday.equals(toMonday)) {
            throw new ApiException(400, "Choose a different week to copy to");
        }
        long shiftDays = ChronoUnit.DAYS.between(fromMonday, toMonday);
        Instant start = fromMonday.atStartOfDay(scheduleService.zone()).toInstant();
        Instant end = fromMonday.plusDays(7).atStartOfDay(scheduleService.zone()).toInstant();
        List<WorkScheduleEntity> source = scheduleRepository.findAtBranches(start, end, List.of(request.branchId())).stream()
                .filter(s -> s.getStatus() != ScheduleStatus.CANCELLED)
                .filter(s -> request.includeOpenShifts() || s.getEmployeeId() != null)
                .toList();
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(source.stream().map(WorkScheduleEntity::getEmployeeId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, e -> e));

        List<WorkScheduleEntity> copies = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (WorkScheduleEntity shift : source) {
            // Same local wall-clock time on the same weekday, so a daylight-saving change cannot shift it.
            Instant newStart = shift.getStartsAt().atZone(scheduleService.zone()).plusDays(shiftDays).toInstant();
            Instant newEnd = shift.getEndsAt().atZone(scheduleService.zone()).plusDays(shiftDays).toInstant();
            EmployeeEntity employee = shift.getEmployeeId() == null ? null : employees.get(shift.getEmployeeId());
            if (employee != null) {
                List<ScheduleRules.Issue> blocking = scheduleService.evaluate(employee, request.branchId(),
                                new ScheduleRules.Interval(newStart, newEnd, shift.getBreakMinutes()), Set.of())
                        .stream().filter(i -> i.severity() == ScheduleRules.Severity.BLOCKING).toList();
                // Copies made earlier in this run count too.
                boolean clashesWithCopy = copies.stream().anyMatch(c -> employee.getId().equals(c.getEmployeeId())
                        && c.getStartsAt().isBefore(newEnd) && newStart.isBefore(c.getEndsAt()));
                if (!blocking.isEmpty() || clashesWithCopy) {
                    skipped.add(new Skipped(employee.fullName(), scheduleService.when(newStart),
                            clashesWithCopy ? "Already has a shift then" : blocking.get(0).message()));
                    continue;
                }
            }
            WorkScheduleEntity copy = new WorkScheduleEntity();
            copy.setEmployeeId(shift.getEmployeeId());
            copy.setUserId(employee == null ? null : employee.getUserId());
            copy.setBranchId(request.branchId());
            copy.setStartsAt(newStart);
            copy.setEndsAt(newEnd);
            copy.setBreakMinutes(shift.getBreakMinutes());
            copy.setTemplateId(shift.getTemplateId());
            copy.setPositionId(shift.getPositionId());
            copy.setPosition(shift.getPosition());
            copy.setDepartmentId(shift.getDepartmentId());
            copy.setNotes(shift.getNotes());
            copy.setStatus(ScheduleStatus.DRAFT);
            copy.setCreatedBy(access.userId());
            copies.add(copy);
        }
        scheduleRepository.saveAll(copies);
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Branch", request.branchId(), Map.of(
                "operation", "week-copied", "from", fromMonday.toString(), "to", toMonday.toString(),
                "copied", copies.size(), "skipped", skipped.size()));
        return new CopyWeekResult(copies.size(), skipped);
    }

    /** Published, unassigned shifts in the coming weeks at the branches this employee works at, that they could take. */
    @Transactional(readOnly = true)
    public List<OpenShift> openShiftsForMe(UserPrincipal principal) {
        EmployeeEntity me = me(principal);
        Instant now = Instant.now();
        List<WorkScheduleEntity> open = scheduleRepository.findAtBranches(now, now.plus(Duration.ofDays(OPEN_SHIFT_DAYS)), List.copyOf(myBranches(me)))
                .stream()
                .filter(s -> s.getStatus() == ScheduleStatus.PUBLISHED && s.getEmployeeId() == null && s.getStartsAt().isAfter(now))
                .toList();
        List<OpenShift> result = new ArrayList<>();
        List<ScheduleService.ShiftView> views = views(open);
        for (int i = 0; i < open.size(); i++) {
            WorkScheduleEntity shift = open.get(i);
            List<ScheduleRules.Issue> issues = scheduleService.evaluate(me, shift.getBranchId(), ScheduleService.interval(shift), Set.of(shift.getId()));
            if (issues.stream().noneMatch(issue -> issue.severity() == ScheduleRules.Severity.BLOCKING)) {
                result.add(new OpenShift(views.get(i), issues));
            }
        }
        return result;
    }

    /** Takes an open shift: the same rules as being scheduled; warnings must be confirmed. The branch's managers are told. */
    @Transactional
    public ScheduleService.ShiftView claimOpenShift(UserPrincipal principal, UUID id, boolean acknowledgeWarnings) {
        EmployeeEntity me = me(principal);
        WorkScheduleEntity shift = scheduleRepository.findById(id).orElseThrow(() -> new ApiException(404, "Shift not found"));
        if (!myBranches(me).contains(shift.getBranchId())) {
            throw new ApiException(404, "Shift not found");
        }
        if (shift.getStatus() != ScheduleStatus.PUBLISHED || shift.getEmployeeId() != null) {
            throw new ApiException(409, "This shift has already been taken");
        }
        if (!shift.getStartsAt().isAfter(Instant.now())) {
            throw new ApiException(409, "This shift has already started");
        }
        List<ScheduleRules.Issue> issues = scheduleService.evaluate(me, shift.getBranchId(), ScheduleService.interval(shift), Set.of(shift.getId()));
        ScheduleService.enforce(issues, acknowledgeWarnings);
        shift.setEmployeeId(me.getId());
        shift.setUserId(me.getUserId());
        try {
            scheduleRepository.saveAndFlush(shift);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ApiException(409, "Someone else took this shift a moment ago");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", "open-shift-claimed");
        meta.put("employeeId", me.getId().toString());
        if (!issues.isEmpty()) meta.put("acknowledgedWarnings", issues.stream().map(ScheduleRules.Issue::code).toList());
        audit.log(principal.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Schedule", shift.getId(), meta);
        notifier.openShiftClaimed(branchManagerUserIds(shift.getBranchId()), me.fullName(), scheduleService.when(shift.getStartsAt()));
        return views(List.of(shift)).get(0);
    }

    private EmployeeEntity me(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
    }

    /** The employee's home branch and any branch they are assigned to today. */
    private Set<UUID> myBranches(EmployeeEntity me) {
        Set<UUID> branches = new HashSet<>();
        if (me.primaryBranchId() != null) branches.add(me.primaryBranchId());
        assignmentRepository.findActiveSecondary(me.getId(), LocalDate.now(scheduleService.zone())).stream()
                .map(BranchAssignmentEntity::getBranchId).forEach(branches::add);
        return branches;
    }

    private List<UUID> branchManagerUserIds(UUID branchId) {
        return userRepository.findByRoleAndActiveTrueOrderByNameAsc(Role.MANAGER).stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branchId))
                .map(UserEntity::getId)
                .toList();
    }

    private List<ScheduleService.ShiftView> views(List<WorkScheduleEntity> shifts) {
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(shifts.stream().map(WorkScheduleEntity::getEmployeeId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, e -> e));
        return scheduleService.toViews(shifts, employees);
    }

    public record CopyWeekRequest(@NotNull UUID branchId, @NotNull LocalDate fromWeek, @NotNull LocalDate toWeek, boolean includeOpenShifts) {}

    public record Skipped(String employeeName, String when, String reason) {}

    public record CopyWeekResult(int copied, List<Skipped> skipped) {}

    /** An open shift and the warnings that would apply to this employee taking it. */
    public record OpenShift(ScheduleService.ShiftView shift, List<ScheduleRules.Issue> warnings) {}
}
