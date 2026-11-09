package com.restaurantmanager.core.hr.attendance;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmploymentStatus;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import com.restaurantmanager.core.workforce.TimeEntryRepository;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Processes clock actions from every source (POS, self-service, manager). Each action is an idempotent event: the same
 * idempotency key always yields the first outcome, so retries after a network failure never double-clock anyone.
 */
@Service
public class AttendanceService {
    /** Queued device times older than this are refused; such gaps go through a correction instead. */
    static final Duration MAX_EVENT_AGE = Duration.ofHours(12);
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    private final TimeEntryRepository entryRepository;
    private final AttendanceBreakRepository breakRepository;
    private final AttendanceEventRepository eventRepository;
    private final AttendanceEventRecorder eventRecorder;
    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAssignmentRepository assignmentRepository;
    private final AttendancePolicyService policyService;
    private final com.restaurantmanager.core.hr.timesheet.TimesheetGuard timesheetGuard;
    private final ZoneId zone;

    public AttendanceService(TimeEntryRepository entryRepository, AttendanceBreakRepository breakRepository,
                             AttendanceEventRepository eventRepository, AttendanceEventRecorder eventRecorder,
                             WorkScheduleRepository scheduleRepository, EmployeeRepository employeeRepository,
                             BranchAssignmentRepository assignmentRepository, AttendancePolicyService policyService,
                             com.restaurantmanager.core.hr.timesheet.TimesheetGuard timesheetGuard,
                             @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.entryRepository = entryRepository;
        this.breakRepository = breakRepository;
        this.eventRepository = eventRepository;
        this.eventRecorder = eventRecorder;
        this.scheduleRepository = scheduleRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.policyService = policyService;
        this.timesheetGuard = timesheetGuard;
        this.zone = ZoneId.of(timeZone);
    }

    public ZoneId zone() {
        return zone;
    }

    /** One clock action. {@code branchId} is the terminal's branch (POS) or null to use the open entry / home branch. */
    public record ClockCommand(UUID employeeId, ClockAction action, BreakType breakType, UUID branchId, AttendanceSource source,
                               String terminalId, String idempotencyKey, Instant occurredAt, UUID actorUserId) {}

    public record ClockResult(UUID eventId, ClockAction action, Instant at, boolean duplicate, EntryView entry, String message) {}

    @Transactional
    public ClockResult clock(ClockCommand cmd) {
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank() || cmd.idempotencyKey().length() > 100) {
            throw new ApiException(400, "A request key (1–100 characters) is required");
        }
        var previous = eventRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (previous.isPresent()) {
            return replay(previous.get(), cmd);
        }
        EmployeeEntity employee = employeeRepository.findById(cmd.employeeId()).orElseThrow(() -> new ApiException(404, "Employee not found"));
        Instant now = Instant.now();
        Instant at = eventTime(cmd.occurredAt(), now);
        try {
            timesheetGuard.assertEditable(employee.getId(), at.atZone(zone).toLocalDate());
            TimeEntryEntity entry = switch (cmd.action()) {
                case CLOCK_IN -> clockIn(employee, cmd, at);
                case BREAK_START -> startBreak(employee, cmd, at);
                case BREAK_END -> endBreak(employee, cmd, at);
                case CLOCK_OUT -> clockOut(employee, cmd, at);
            };
            AttendanceEventEntity event = new AttendanceEventEntity();
            event.setIdempotencyKey(cmd.idempotencyKey());
            event.setEmployeeId(employee.getId());
            event.setEventType(cmd.action());
            event.setOccurredAt(at);
            event.setSource(cmd.source().name());
            event.setTerminalId(cmd.terminalId());
            event.setBranchId(entry.getBranchId());
            event.setTimeEntryId(entry.getId());
            event.setOutcome("ACCEPTED");
            event.setRecordedBy(cmd.actorUserId());
            eventRepository.saveAndFlush(event);
            return new ClockResult(event.getId(), cmd.action(), at, false, views(List.of(entry), now).get(0), message(cmd.action(), employee, entry));
        } catch (ApiException ex) {
            eventRecorder.rejected(cmd.idempotencyKey(), employee.getId(), cmd.action(), at, cmd.source(), cmd.terminalId(), cmd.branchId(),
                    cmd.actorUserId(), ex.getMessage());
            throw ex;
        }
    }

    /** Returns the stored outcome for a key that was already processed. */
    @Transactional(readOnly = true)
    public ClockResult replayKey(ClockCommand cmd) {
        AttendanceEventEntity event = eventRepository.findByIdempotencyKey(cmd.idempotencyKey())
                .orElseThrow(() -> new ApiException(409, "This action conflicts with another one just recorded. Check the status and try again."));
        return replay(event, cmd);
    }

    private ClockResult replay(AttendanceEventEntity event, ClockCommand cmd) {
        if (!event.getEmployeeId().equals(cmd.employeeId()) || event.getEventType() != cmd.action()) {
            throw new ApiException(409, "This request key was already used for a different action");
        }
        if ("REJECTED".equals(event.getOutcome())) {
            throw new ApiException(409, event.getMessage() == null ? "This action was refused" : event.getMessage());
        }
        TimeEntryEntity entry = event.getTimeEntryId() == null ? null : entryRepository.findById(event.getTimeEntryId()).orElse(null);
        EmployeeEntity employee = employeeRepository.findById(event.getEmployeeId()).orElse(null);
        return new ClockResult(event.getId(), event.getEventType(), event.getOccurredAt(), true,
                entry == null ? null : views(List.of(entry), Instant.now()).get(0),
                entry == null || employee == null ? "Already recorded" : message(event.getEventType(), employee, entry));
    }

    // ------------------------------------------------------------ actions

    private TimeEntryEntity clockIn(EmployeeEntity employee, ClockCommand cmd, Instant at) {
        requireCanWork(employee);
        entryRepository.findFirstByEmployeeIdAndClockOutAtIsNull(employee.getId()).ifPresent(open -> {
            throw new ApiException(409, employee.fullName() + " is already clocked in (since " + time(open.getClockInAt()) + ")");
        });
        UUID branchId = cmd.branchId() != null ? cmd.branchId() : employee.primaryBranchId();
        if (branchId == null) {
            throw new ApiException(400, "Choose the branch to clock in at");
        }
        if (!assignmentRepository.existsActiveAt(employee.getId(), branchId, at.atZone(zone).toLocalDate())) {
            throw new ApiException(403, employee.fullName() + " is not assigned to this branch today");
        }
        AttendanceCalculator.Policy policy = policyService.effective(branchId);
        WorkScheduleEntity shift = matchingShift(employee.getId(), branchId, at, policy);
        if (shift == null && "BLOCK".equals(policy.unscheduledClockIn())) {
            throw new ApiException(409, employee.fullName() + " has no shift starting now. Ask a manager to add one or clock you in.");
        }
        TimeEntryEntity entry = new TimeEntryEntity();
        entry.setEmployeeId(employee.getId());
        entry.setUserId(employee.getUserId());
        entry.setBranchId(branchId);
        entry.setScheduleId(shift == null ? null : shift.getId());
        entry.setUnscheduled(shift == null);
        entry.setClockInAt(at);
        entry.setSource(cmd.source().name());
        entry.setTerminalId(cmd.terminalId());
        return entryRepository.saveAndFlush(entry);
    }

    private TimeEntryEntity startBreak(EmployeeEntity employee, ClockCommand cmd, Instant at) {
        TimeEntryEntity entry = openEntry(employee, cmd);
        requireNotBefore(at, latestActivity(entry), "the last clock action");
        if (breakRepository.findFirstByTimeEntryIdAndEndedAtIsNull(entry.getId()).isPresent()) {
            throw new ApiException(409, employee.fullName() + " is already on a break");
        }
        BreakType type = cmd.breakType() == null ? BreakType.REST : cmd.breakType();
        AttendanceBreakEntity brk = new AttendanceBreakEntity();
        brk.setTimeEntryId(entry.getId());
        brk.setBreakType(type);
        brk.setPaid(policyService.effective(entry.getBranchId()).breakPaid(type));
        brk.setStartedAt(at);
        brk.setSource(cmd.source().name());
        breakRepository.saveAndFlush(brk);
        return entry;
    }

    private TimeEntryEntity endBreak(EmployeeEntity employee, ClockCommand cmd, Instant at) {
        TimeEntryEntity entry = openEntry(employee, cmd);
        AttendanceBreakEntity brk = breakRepository.findFirstByTimeEntryIdAndEndedAtIsNull(entry.getId())
                .orElseThrow(() -> new ApiException(409, employee.fullName() + " is not on a break"));
        requireNotBefore(at, brk.getStartedAt(), "the start of the break");
        brk.setEndedAt(at);
        breakRepository.saveAndFlush(brk);
        entry.setBreakMinutes(unpaidBreakMinutes(entry.getId(), at));
        return entryRepository.saveAndFlush(entry);
    }

    private TimeEntryEntity clockOut(EmployeeEntity employee, ClockCommand cmd, Instant at) {
        TimeEntryEntity entry = openEntry(employee, cmd);
        requireNotBefore(at, latestActivity(entry), "the last clock action");
        breakRepository.findFirstByTimeEntryIdAndEndedAtIsNull(entry.getId()).ifPresent(open -> {
            open.setEndedAt(at); // clocking out ends a break left open
            breakRepository.saveAndFlush(open);
        });
        entry.setClockOutAt(at);
        entry.setClockOutSource(cmd.source().name());
        entry.setBreakMinutes(unpaidBreakMinutes(entry.getId(), at));
        return entryRepository.saveAndFlush(entry);
    }

    // ------------------------------------------------------------ helpers

    private TimeEntryEntity openEntry(EmployeeEntity employee, ClockCommand cmd) {
        TimeEntryEntity entry = entryRepository.findFirstByEmployeeIdAndClockOutAtIsNull(employee.getId())
                .orElseThrow(() -> new ApiException(409, employee.fullName() + " is not clocked in"));
        if (cmd.source() == AttendanceSource.POS && cmd.branchId() != null && entry.getBranchId() != null
                && !entry.getBranchId().equals(cmd.branchId())) {
            throw new ApiException(409, employee.fullName() + " is clocked in at another branch; clock out there");
        }
        return entry;
    }

    private static void requireCanWork(EmployeeEntity employee) {
        EmploymentStatus status = employee.getEmploymentStatus();
        if (status.isExit()) {
            throw new ApiException(403, "This person no longer works here");
        }
        if (status == EmploymentStatus.SUSPENDED) {
            throw new ApiException(403, employee.fullName() + " is suspended and cannot clock in");
        }
    }

    private WorkScheduleEntity matchingShift(UUID employeeId, UUID branchId, Instant at, AttendanceCalculator.Policy policy) {
        Instant from = at.minus(Duration.ofHours(24));
        Instant to = at.plus(Duration.ofMinutes(policy.earlyClockInMinutes() + 1L));
        return scheduleRepository.findPublishedForEmployee(from, to, employeeId).stream()
                .filter(s -> branchId.equals(s.getBranchId()))
                .filter(s -> AttendanceCalculator.withinShiftWindow(at, s.getStartsAt(), s.getEndsAt(), policy))
                .min(Comparator.comparing(s -> Duration.between(s.getStartsAt(), at).abs()))
                .orElse(null);
    }

    private Instant latestActivity(TimeEntryEntity entry) {
        Instant latest = entry.getClockInAt();
        for (AttendanceBreakEntity b : breakRepository.findByTimeEntryIdOrderByStartedAtAsc(entry.getId())) {
            Instant last = b.getEndedAt() != null ? b.getEndedAt() : b.getStartedAt();
            if (last.isAfter(latest)) {
                latest = last;
            }
        }
        return latest;
    }

    private static void requireNotBefore(Instant at, Instant earliest, String what) {
        if (at.isBefore(earliest)) {
            throw new ApiException(409, "This action is timed before " + what + "; ask a manager to correct the record");
        }
    }

    int unpaidBreakMinutes(UUID entryId, Instant now) {
        return (int) breakRepository.findByTimeEntryIdOrderByStartedAtAsc(entryId).stream()
                .filter(b -> !b.isPaid())
                .mapToLong(b -> b.minutes(now))
                .sum();
    }

    static Instant eventTime(Instant occurredAt, Instant now) {
        if (occurredAt == null) {
            return now;
        }
        if (occurredAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new ApiException(400, "The device clock is ahead of the server; check the terminal's time");
        }
        if (occurredAt.isBefore(now.minus(MAX_EVENT_AGE))) {
            throw new ApiException(400, "This clock action is too old to record automatically; ask a manager for a correction");
        }
        return occurredAt.isAfter(now) ? now : occurredAt;
    }

    private String message(ClockAction action, EmployeeEntity employee, TimeEntryEntity entry) {
        String name = employee.getFirstName();
        return switch (action) {
            case CLOCK_IN -> name + " clocked in at " + time(entry.getClockInAt()) + (entry.isUnscheduled() ? " (no shift scheduled — flagged for your manager)" : "");
            case BREAK_START -> name + " started a break";
            case BREAK_END -> name + " is back from break";
            case CLOCK_OUT -> name + " clocked out at " + time(entry.getClockOutAt());
        };
    }

    String time(Instant instant) {
        return instant == null ? "" : String.format("%tR", instant.atZone(zone));
    }

    // ------------------------------------------------------------ views

    public record BreakView(UUID id, BreakType type, boolean paid, Instant startedAt, Instant endedAt, long minutes) {}

    public record EntryView(UUID id, UUID employeeId, String employeeName, UUID branchId, UUID scheduleId, Instant shiftStartsAt,
                            Instant shiftEndsAt, Instant clockInAt, Instant clockOutAt, String source, String clockOutSource,
                            String terminalId, boolean unscheduled, boolean onBreak, List<BreakView> breaks,
                            AttendanceCalculator.Metrics metrics, String status, String editReason) {}

    /** Batch view building: one query each for employees, shifts and breaks. */
    public List<EntryView> views(List<TimeEntryEntity> entries, Instant now) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(entries.stream().map(TimeEntryEntity::getEmployeeId)
                .filter(Objects::nonNull).distinct().toList()).stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        Map<UUID, WorkScheduleEntity> shifts = scheduleRepository.findAllById(entries.stream().map(TimeEntryEntity::getScheduleId)
                .filter(Objects::nonNull).distinct().toList()).stream().collect(Collectors.toMap(WorkScheduleEntity::getId, Function.identity()));
        Map<UUID, List<AttendanceBreakEntity>> breaks = breakRepository.findByTimeEntryIdIn(entries.stream().map(TimeEntryEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(AttendanceBreakEntity::getTimeEntryId));
        Map<UUID, AttendanceCalculator.Policy> policies = new HashMap<>();
        List<EntryView> views = new ArrayList<>();
        for (TimeEntryEntity e : entries) {
            EmployeeEntity employee = e.getEmployeeId() == null ? null : employees.get(e.getEmployeeId());
            WorkScheduleEntity shift = e.getScheduleId() == null ? null : shifts.get(e.getScheduleId());
            List<AttendanceBreakEntity> entryBreaks = breaks.getOrDefault(e.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(AttendanceBreakEntity::getStartedAt)).toList();
            AttendanceCalculator.Policy policy = policies.computeIfAbsent(e.getBranchId() == null ? new UUID(0, 0) : e.getBranchId(),
                    k -> policyService.effective(e.getBranchId()));
            long unpaid = e.getClockOutAt() == null
                    ? entryBreaks.stream().filter(b -> !b.isPaid()).mapToLong(b -> b.minutes(now)).sum()
                    : e.getBreakMinutes();
            AttendanceCalculator.Metrics metrics = AttendanceCalculator.compute(e.getClockInAt(), e.getClockOutAt(), unpaid,
                    shift == null ? null : new AttendanceCalculator.Scheduled(shift.getStartsAt(), shift.getEndsAt(), shift.getBreakMinutes()),
                    policy, now);
            boolean onBreak = e.getClockOutAt() == null && entryBreaks.stream().anyMatch(b -> b.getEndedAt() == null);
            String status = e.getClockOutAt() != null ? "COMPLETED" : metrics.missedClockOut() ? "MISSED_CLOCK_OUT" : onBreak ? "ON_BREAK" : "ON_SHIFT";
            views.add(new EntryView(e.getId(), e.getEmployeeId(), employee == null ? null : employee.fullName(), e.getBranchId(), e.getScheduleId(),
                    shift == null ? null : shift.getStartsAt(), shift == null ? null : shift.getEndsAt(), e.getClockInAt(), e.getClockOutAt(),
                    e.getSource(), e.getClockOutSource(), e.getTerminalId(), e.isUnscheduled(), onBreak,
                    entryBreaks.stream().map(b -> new BreakView(b.getId(), b.getBreakType(), b.isPaid(), b.getStartedAt(), b.getEndedAt(), b.minutes(now))).toList(),
                    metrics, status, e.getEditReason()));
        }
        return views;
    }
}
