package com.restaurantmanager.core.hr.attendance;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import com.restaurantmanager.core.workforce.TimeEntryRepository;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Manager attendance views, clocking on behalf, clock PINs, corrections, and employee self-service attendance. */
@Service
public class AttendanceManagementService {
    private final TimeEntryRepository entryRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceBreakRepository breakRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final BranchAssignmentRepository assignmentRepository;
    private final AttendanceService attendanceService;
    private final AttendancePolicyService policyService;
    private final ClockGateway gateway;
    private final ClockPinService pinService;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final com.restaurantmanager.core.hr.timesheet.TimesheetGuard timesheetGuard;
    private final com.restaurantmanager.core.hr.leave.LeaveLookup leaveLookup;

    public AttendanceManagementService(TimeEntryRepository entryRepository, AttendanceCorrectionRepository correctionRepository,
                                       AttendanceBreakRepository breakRepository, WorkScheduleRepository scheduleRepository,
                                       EmployeeRepository employeeRepository, EmployeeService employeeService,
                                       BranchAssignmentRepository assignmentRepository, AttendanceService attendanceService,
                                       AttendancePolicyService policyService, ClockGateway gateway, ClockPinService pinService,
                                       HrAccessService accessService, HrAuditService audit,
                                       com.restaurantmanager.core.hr.timesheet.TimesheetGuard timesheetGuard,
                                       com.restaurantmanager.core.hr.leave.LeaveLookup leaveLookup) {
        this.entryRepository = entryRepository;
        this.correctionRepository = correctionRepository;
        this.breakRepository = breakRepository;
        this.scheduleRepository = scheduleRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.assignmentRepository = assignmentRepository;
        this.attendanceService = attendanceService;
        this.policyService = policyService;
        this.gateway = gateway;
        this.pinService = pinService;
        this.accessService = accessService;
        this.audit = audit;
        this.timesheetGuard = timesheetGuard;
        this.leaveLookup = leaveLookup;
    }

    // ------------------------------------------------------------ manager views

    /** Everyone scheduled or clocked in at a branch on one day, with their status. Absent = shift started, no clock-in. */
    @Transactional(readOnly = true)
    public DayView day(UserPrincipal principal, UUID branchId, LocalDate date) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_VIEW);
        access.assertBranch(branchId);
        Instant from = date.atStartOfDay(attendanceService.zone()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(attendanceService.zone()).toInstant();
        Instant now = Instant.now();
        // records of logins without an employee record belong to the legacy time clock only
        List<TimeEntryEntity> entries = entryRepository.findAtBranches(from, to, List.of(branchId)).stream()
                .filter(e -> e.getEmployeeId() != null).toList();
        List<WorkScheduleEntity> shifts = scheduleRepository.findAtBranches(from, to, List.of(branchId)).stream()
                .filter(s -> s.getStatus() == ScheduleStatus.PUBLISHED && s.getEmployeeId() != null).toList();
        Map<UUID, AttendanceService.EntryView> byEntry = attendanceService.views(entries, now).stream()
                .collect(Collectors.toMap(AttendanceService.EntryView::id, Function.identity()));
        Set<UUID> linkedShifts = entries.stream().map(TimeEntryEntity::getScheduleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> employeeIds = new HashSet<>();
        entries.forEach(e -> employeeIds.add(e.getEmployeeId()));
        shifts.forEach(s -> employeeIds.add(s.getEmployeeId()));
        employeeIds.remove(null);
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(employeeIds).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));

        Set<UUID> onLeave = leaveLookup.onApprovedLeave(employeeIds, date);
        List<DayRow> rows = new ArrayList<>();
        for (TimeEntryEntity e : entries) {
            AttendanceService.EntryView view = byEntry.get(e.getId());
            String status = view.unscheduled() && view.clockOutAt() == null ? "UNSCHEDULED" : view.status();
            if ("COMPLETED".equals(status) && view.metrics().earlyLeaveMinutes() > 0) {
                status = "LEFT_EARLY";
            }
            rows.add(new DayRow(e.getEmployeeId(), name(employees, e.getEmployeeId()), view.shiftStartsAt(), view.shiftEndsAt(), view,
                    status, view.metrics().lateMinutes() > 0));
        }
        for (WorkScheduleEntity s : shifts) {
            if (linkedShifts.contains(s.getId())) {
                continue;
            }
            String status = onLeave.contains(s.getEmployeeId()) ? "ON_LEAVE" : now.isBefore(s.getStartsAt()) ? "NOT_STARTED" : "ABSENT";
            rows.add(new DayRow(s.getEmployeeId(), name(employees, s.getEmployeeId()), s.getStartsAt(), s.getEndsAt(), null, status, false));
        }
        rows.sort(Comparator.comparing((DayRow r) -> r.shiftStartsAt() != null ? r.shiftStartsAt()
                : r.entry() != null ? r.entry().clockInAt() : Instant.MAX).thenComparing(r -> r.employeeName() == null ? "" : r.employeeName()));
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(DayRow::status, LinkedHashMap::new, Collectors.counting()));
        long late = rows.stream().filter(DayRow::late).count();
        return new DayView(branchId, date, rows, counts, late, policyService.effective(branchId));
    }

    @Transactional(readOnly = true)
    public List<AttendanceService.EntryView> entries(UserPrincipal principal, UUID branchId, LocalDate from, LocalDate to, UUID employeeId) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_VIEW);
        access.assertBranch(branchId);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) {
            throw new ApiException(400, "Choose a range of at most 62 days");
        }
        Instant start = from.atStartOfDay(attendanceService.zone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(attendanceService.zone()).toInstant();
        List<TimeEntryEntity> rows = entryRepository.findAtBranches(start, end, List.of(branchId)).stream()
                .filter(e -> e.getEmployeeId() != null && (employeeId == null || employeeId.equals(e.getEmployeeId()))).toList();
        return attendanceService.views(rows, Instant.now());
    }

    /** A manager records an action for an employee (e.g. forgot to clock in at the POS). */
    public AttendanceService.ClockResult clockFor(UserPrincipal principal, ManagerClockRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_MANAGE);
        access.assertBranch(request.branchId());
        employeeService.loadVisible(request.employeeId(), access);
        AttendanceService.ClockResult result = gateway.clock(new AttendanceService.ClockCommand(request.employeeId(), request.action(),
                request.breakType(), request.branchId(), AttendanceSource.MANAGER, null, request.idempotencyKey(), null, access.userId()));
        if (!result.duplicate()) {
            audit.log(access.userId(), AuditAction.HR_ATTENDANCE_RECORDED, "Employee", request.employeeId(),
                    Map.of("action", request.action().name(), "source", "MANAGER", "reason", String.valueOf(request.reason())));
        }
        return result;
    }

    // ------------------------------------------------------------ clock PINs

    @Transactional(readOnly = true)
    public PinStatus pinStatus(UserPrincipal principal, UUID employeeId) {
        HrAccess access = accessService.requireAny(principal, HrPermission.ATTENDANCE_MANAGE, HrPermission.ATTENDANCE_VIEW);
        EmployeeEntity e = employeeService.loadInScope(employeeId, access);
        return pinStatus(e);
    }

    @Transactional
    public PinStatus setPin(UserPrincipal principal, UUID employeeId, String pin) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_MANAGE);
        EmployeeEntity e = employeeService.loadInScope(employeeId, access);
        pinService.set(e, pin);
        audit.log(access.userId(), AuditAction.HR_CLOCK_PIN_CHANGED, "Employee", employeeId, Map.of("operation", "set"));
        return pinStatus(e);
    }

    @Transactional
    public PinStatus clearPin(UserPrincipal principal, UUID employeeId) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_MANAGE);
        EmployeeEntity e = employeeService.loadInScope(employeeId, access);
        pinService.clear(e);
        audit.log(access.userId(), AuditAction.HR_CLOCK_PIN_CHANGED, "Employee", employeeId, Map.of("operation", "cleared"));
        return pinStatus(e);
    }

    private static PinStatus pinStatus(EmployeeEntity e) {
        boolean locked = e.getClockPinLockedUntil() != null && e.getClockPinLockedUntil().isAfter(Instant.now());
        return new PinStatus(e.getClockPinHash() != null, e.getClockPinSetAt(), locked ? e.getClockPinLockedUntil() : null);
    }

    // ------------------------------------------------------------ corrections

    @Transactional(readOnly = true)
    public List<CorrectionView> corrections(UserPrincipal principal, CorrectionStatus status) {
        HrAccess access = accessService.requireAny(principal, HrPermission.ATTENDANCE_CORRECT, HrPermission.ATTENDANCE_VIEW);
        List<AttendanceCorrectionEntity> rows = status == null ? correctionRepository.findTop200ByOrderByCreatedAtDesc()
                : correctionRepository.findByStatusOrderByCreatedAtAsc(status);
        return views(rows.stream().filter(c -> access.canSeeBranch(c.getBranchId())).toList());
    }

    /**
     * Requests a correction: of an existing entry (timeEntryId) or a missing one (employeeId + branchId). Applied at once
     * when the branch policy does not require approval; otherwise waits for a different person with the same permission.
     */
    @Transactional
    public CorrectionView requestCorrection(UserPrincipal principal, CorrectionRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_CORRECT);
        TimeEntryEntity entry = null;
        UUID employeeId;
        UUID branchId;
        if (r.timeEntryId() != null) {
            entry = entryRepository.findById(r.timeEntryId()).orElseThrow(() -> new ApiException(404, "Attendance record not found"));
            if (!access.canSeeBranch(entry.getBranchId()) || entry.getEmployeeId() == null) {
                throw new ApiException(404, "Attendance record not found");
            }
            if (correctionRepository.existsByTimeEntryIdAndStatus(entry.getId(), CorrectionStatus.PENDING)) {
                throw new ApiException(409, "A correction for this record is already waiting for approval");
            }
            employeeId = entry.getEmployeeId();
            branchId = entry.getBranchId();
        } else {
            if (r.employeeId() == null || r.branchId() == null) {
                throw new ApiException(400, "Choose the attendance record, or the employee and branch for a missing one");
            }
            access.assertBranch(r.branchId());
            employeeService.loadVisible(r.employeeId(), access);
            employeeId = r.employeeId();
            branchId = r.branchId();
            LocalDate day = r.clockInAt().atZone(attendanceService.zone()).toLocalDate();
            if (!assignmentRepository.existsActiveAt(employeeId, branchId, day)) {
                throw new ApiException(400, "The employee was not assigned to this branch on that date");
            }
        }
        employeeRepository.findByUserId(access.userId()).ifPresent(me -> {
            if (me.getId().equals(employeeId)) {
                throw new ApiException(403, "You cannot correct your own attendance");
            }
        });
        Instant now = Instant.now();
        if (r.clockInAt().isAfter(now) || (r.clockOutAt() != null && r.clockOutAt().isAfter(now.plus(Duration.ofMinutes(1))))) {
            throw new ApiException(400, "Corrections cannot be in the future");
        }
        if (r.clockOutAt() != null && !r.clockOutAt().isAfter(r.clockInAt())) {
            throw new ApiException(400, "Clock-out must be after clock-in");
        }
        if (r.clockOutAt() == null && entry == null) {
            throw new ApiException(400, "A missing record needs both clock-in and clock-out times");
        }
        timesheetGuard.assertEditable(employeeId, day(r.clockInAt()), entry == null ? null : day(entry.getClockInAt()));
        int breaks = r.breakMinutes() == null ? 0 : r.breakMinutes();
        if (r.clockOutAt() != null && breaks >= Duration.between(r.clockInAt(), r.clockOutAt()).toMinutes()) {
            throw new ApiException(400, "Unpaid break time is longer than the shift");
        }
        AttendanceCorrectionEntity c = new AttendanceCorrectionEntity();
        c.setTimeEntryId(entry == null ? null : entry.getId());
        c.setEmployeeId(employeeId);
        c.setBranchId(branchId);
        if (entry != null) {
            c.setOriginalClockIn(entry.getClockInAt());
            c.setOriginalClockOut(entry.getClockOutAt());
            c.setOriginalBreakMinutes(entry.getBreakMinutes());
        }
        c.setCorrectedClockIn(r.clockInAt());
        c.setCorrectedClockOut(r.clockOutAt());
        c.setCorrectedBreakMinutes(breaks);
        c.setReason(r.reason().trim());
        c.setRequestedBy(access.userId());
        c.setStatus(CorrectionStatus.PENDING);
        correctionRepository.save(c);
        audit.log(access.userId(), AuditAction.HR_ATTENDANCE_CORRECTION, "Employee", employeeId,
                Map.of("operation", "requested", "correctionId", c.getId().toString()));
        if (!policyService.effective(branchId).requireCorrectionApproval()) {
            apply(c, access.userId(), "Applied directly (approval not required by policy)");
        }
        return views(List.of(c)).get(0);
    }

    @Transactional
    public CorrectionView decide(UserPrincipal principal, UUID id, CorrectionDecision decision) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_CORRECT);
        AttendanceCorrectionEntity c = correctionRepository.findById(id).orElseThrow(() -> new ApiException(404, "Correction not found"));
        if (!access.canSeeBranch(c.getBranchId())) {
            throw new ApiException(404, "Correction not found");
        }
        if (c.getStatus() != CorrectionStatus.PENDING) {
            throw new ApiException(409, "This correction has already been decided");
        }
        if (access.userId().equals(c.getRequestedBy())) {
            throw new ApiException(403, "Someone else must approve a correction you requested");
        }
        employeeRepository.findByUserId(access.userId()).ifPresent(me -> {
            if (me.getId().equals(c.getEmployeeId())) {
                throw new ApiException(403, "You cannot decide a correction to your own attendance");
            }
        });
        if (decision.approve()) {
            apply(c, access.userId(), decision.note());
        } else {
            c.setStatus(CorrectionStatus.REJECTED);
            c.setDecidedBy(access.userId());
            c.setDecidedAt(Instant.now());
            c.setDecisionNote(decision.note() == null || decision.note().isBlank() ? null : decision.note().trim());
            audit.log(access.userId(), AuditAction.HR_ATTENDANCE_CORRECTION, "Employee", c.getEmployeeId(),
                    Map.of("operation", "rejected", "correctionId", id.toString()));
        }
        return views(List.of(c)).get(0);
    }

    private LocalDate day(Instant instant) {
        return instant == null ? null : instant.atZone(attendanceService.zone()).toLocalDate();
    }

    /** Applies a correction: the original values stay on the correction row; the entry takes the corrected values. */
    private void apply(AttendanceCorrectionEntity c, UUID actorId, String note) {
        timesheetGuard.assertEditable(c.getEmployeeId(), day(c.getCorrectedClockIn()), day(c.getOriginalClockIn()));
        TimeEntryEntity entry;
        if (c.getTimeEntryId() != null) {
            entry = entryRepository.findById(c.getTimeEntryId()).orElseThrow(() -> new ApiException(409, "The attendance record no longer exists"));
            if (entry.getClockOutAt() != null && c.getOriginalClockOut() == null && c.getCorrectedClockOut() == null) {
                throw new ApiException(409, "The record changed after the correction was requested");
            }
        } else {
            EmployeeEntity employee = employeeRepository.findById(c.getEmployeeId()).orElseThrow();
            entry = new TimeEntryEntity();
            entry.setEmployeeId(employee.getId());
            entry.setUserId(employee.getUserId());
            entry.setBranchId(c.getBranchId());
            entry.setSource(AttendanceSource.CORRECTION.name());
            WorkScheduleEntity shift = scheduleRepository.findPublishedForEmployee(c.getCorrectedClockIn().minus(Duration.ofHours(12)),
                            c.getCorrectedClockIn().plus(Duration.ofHours(12)), employee.getId()).stream()
                    .filter(s -> c.getBranchId().equals(s.getBranchId()))
                    .min(Comparator.comparing(s -> Duration.between(s.getStartsAt(), c.getCorrectedClockIn()).abs())).orElse(null);
            entry.setScheduleId(shift == null ? null : shift.getId());
            entry.setUnscheduled(shift == null);
        }
        overlapCheck(c, entry);
        entry.setClockInAt(c.getCorrectedClockIn());
        entry.setClockOutAt(c.getCorrectedClockOut());
        if (c.getCorrectedClockOut() != null && entry.getClockOutSource() == null) {
            entry.setClockOutSource(AttendanceSource.CORRECTION.name());
        }
        entry.setBreakMinutes(c.getCorrectedBreakMinutes());
        if (c.getCorrectedClockOut() != null && entry.getId() != null) {
            // a correction that closes the record also closes a break left open
            breakRepository.findFirstByTimeEntryIdAndEndedAtIsNull(entry.getId()).ifPresent(open ->
                    open.setEndedAt(open.getStartedAt().isAfter(c.getCorrectedClockOut()) ? open.getStartedAt() : c.getCorrectedClockOut()));
        }
        entry.setEditedBy(actorId);
        entry.setEditReason(c.getReason());
        TimeEntryEntity saved = entryRepository.saveAndFlush(entry);
        c.setTimeEntryId(saved.getId());
        c.setStatus(CorrectionStatus.APPLIED);
        c.setDecidedBy(actorId);
        c.setDecidedAt(Instant.now());
        c.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", "applied");
        meta.put("correctionId", c.getId().toString());
        meta.put("timeEntryId", saved.getId().toString());
        audit.log(actorId, AuditAction.HR_ATTENDANCE_CORRECTION, "Employee", c.getEmployeeId(), meta);
    }

    private void overlapCheck(AttendanceCorrectionEntity c, TimeEntryEntity self) {
        Instant start = c.getCorrectedClockIn();
        Instant end = c.getCorrectedClockOut() != null ? c.getCorrectedClockOut() : Instant.now();
        boolean overlaps = entryRepository.findForEmployee(c.getEmployeeId(), start.minus(Duration.ofDays(2)), end).stream()
                .filter(e -> !e.getId().equals(self.getId()))
                .anyMatch(e -> e.getClockInAt().isBefore(end) && (e.getClockOutAt() == null || e.getClockOutAt().isAfter(start)));
        if (overlaps) {
            throw new ApiException(409, "The corrected times overlap another attendance record");
        }
    }

    // ------------------------------------------------------------ self-service

    @Transactional(readOnly = true)
    public AttendanceService.EntryView myStatus(UserPrincipal principal) {
        EmployeeEntity me = me(principal);
        return entryRepository.findFirstByEmployeeIdAndClockOutAtIsNull(me.getId())
                .map(e -> attendanceService.views(List.of(e), Instant.now()).get(0)).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceService.EntryView> myHistory(UserPrincipal principal, LocalDate from, LocalDate to) {
        EmployeeEntity me = me(principal);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) {
            throw new ApiException(400, "Choose a range of at most 62 days");
        }
        return attendanceService.views(entryRepository.findForEmployee(me.getId(), from.atStartOfDay(attendanceService.zone()).toInstant(),
                to.plusDays(1).atStartOfDay(attendanceService.zone()).toInstant()), Instant.now());
    }

    public AttendanceService.ClockResult selfClock(UserPrincipal principal, SelfClockRequest request) {
        EmployeeEntity me = me(principal);
        UUID branchId = entryRepository.findFirstByEmployeeIdAndClockOutAtIsNull(me.getId()).map(TimeEntryEntity::getBranchId)
                .orElse(me.primaryBranchId());
        if (!policyService.effective(branchId).allowSelfClock()) {
            throw new ApiException(403, "Clock in and out at the POS terminal at your branch");
        }
        return gateway.clock(new AttendanceService.ClockCommand(me.getId(), request.action(), request.breakType(), null,
                AttendanceSource.SELF_SERVICE, null, request.idempotencyKey(), null, principal.userId()));
    }

    /**
     * Not one transaction on purpose: the current-PIN check records attempts in its own transaction, and the new PIN is then
     * saved on a freshly loaded row so the two never conflict.
     */
    public PinStatus setMyPin(UserPrincipal principal, MyPinRequest request) {
        EmployeeEntity me = me(principal);
        if (me.getClockPinHash() != null) {
            ClockPinService.requireOk(pinService.verify(me.getId(), request.currentPin()));
        }
        EmployeeEntity updated = pinService.setById(me.getId(), request.newPin());
        audit.log(principal.userId(), AuditAction.HR_CLOCK_PIN_CHANGED, "Employee", updated.getId(), Map.of("operation", "self-set"));
        return pinStatus(updated);
    }

    @Transactional(readOnly = true)
    public PinStatus myPin(UserPrincipal principal) {
        return pinStatus(me(principal));
    }

    private EmployeeEntity me(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
    }

    private static String name(Map<UUID, EmployeeEntity> employees, UUID id) {
        EmployeeEntity e = id == null ? null : employees.get(id);
        return e == null ? null : e.fullName();
    }

    private List<CorrectionView> views(List<AttendanceCorrectionEntity> rows) {
        Map<UUID, String> names = new HashMap<>();
        employeeRepository.findByIdIn(rows.stream().map(AttendanceCorrectionEntity::getEmployeeId).distinct().toList())
                .forEach(e -> names.put(e.getId(), e.fullName()));
        return rows.stream().map(c -> new CorrectionView(c.getId(), c.getTimeEntryId(), c.getEmployeeId(), names.get(c.getEmployeeId()),
                c.getBranchId(), c.getOriginalClockIn(), c.getOriginalClockOut(), c.getOriginalBreakMinutes(), c.getCorrectedClockIn(),
                c.getCorrectedClockOut(), c.getCorrectedBreakMinutes(), c.getReason(), c.getStatus(), c.getRequestedBy(), c.getDecidedAt(),
                c.getDecisionNote(), c.getCreatedAt())).toList();
    }

    // ------------------------------------------------------------ DTOs

    public record DayRow(UUID employeeId, String employeeName, Instant shiftStartsAt, Instant shiftEndsAt,
                         AttendanceService.EntryView entry, String status, boolean late) {}

    public record DayView(UUID branchId, LocalDate date, List<DayRow> rows, Map<String, Long> counts, long lateArrivals,
                          AttendanceCalculator.Policy policy) {}

    public record ManagerClockRequest(@NotNull UUID employeeId, @NotNull UUID branchId, @NotNull ClockAction action, BreakType breakType,
                                      @NotBlank @Size(max = 100) String idempotencyKey, @Size(max = 300) String reason) {}

    public record SelfClockRequest(@NotNull ClockAction action, BreakType breakType, @NotBlank @Size(max = 100) String idempotencyKey) {}

    public record PinRequest(@NotBlank @Size(max = 6) String pin) {}

    public record MyPinRequest(@Size(max = 6) String currentPin, @NotBlank @Size(max = 6) String newPin) {}

    public record PinStatus(boolean set, Instant setAt, Instant lockedUntil) {}

    public record CorrectionRequest(UUID timeEntryId, UUID employeeId, UUID branchId, @NotNull Instant clockInAt, Instant clockOutAt,
                                    @Min(0) @Max(720) Integer breakMinutes, @NotBlank @Size(max = 500) String reason) {}

    public record CorrectionDecision(boolean approve, @Size(max = 500) String note) {}

    public record CorrectionView(UUID id, UUID timeEntryId, UUID employeeId, String employeeName, UUID branchId,
                                 Instant originalClockIn, Instant originalClockOut, Integer originalBreakMinutes,
                                 Instant correctedClockIn, Instant correctedClockOut, int correctedBreakMinutes, String reason,
                                 CorrectionStatus status, UUID requestedBy, Instant decidedAt, String decisionNote, Instant createdAt) {}
}
