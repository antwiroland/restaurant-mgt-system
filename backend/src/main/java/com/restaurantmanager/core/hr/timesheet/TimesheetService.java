package com.restaurantmanager.core.hr.timesheet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.attendance.AttendanceCorrectionRepository;
import com.restaurantmanager.core.hr.attendance.AttendanceService;
import com.restaurantmanager.core.hr.attendance.CorrectionStatus;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import com.restaurantmanager.core.workforce.TimeEntryRepository;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Timesheets: generated from attendance as frozen snapshots, then Draft → Submitted → Reviewed → Approved → Locked.
 * Overtime only becomes payable once approved (or automatically when the rules in force do not require approval).
 */
@Service
public class TimesheetService {
    static final int MAX_PERIOD_DAYS = 31;

    private final TimesheetRepository timesheetRepository;
    private final TimesheetLineRepository lineRepository;
    private final TimeEntryRepository entryRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceService attendanceService;
    private final OvertimeSettingsService settings;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ObjectMapper objectMapper;

    public TimesheetService(TimesheetRepository timesheetRepository, TimesheetLineRepository lineRepository, TimeEntryRepository entryRepository,
                            WorkScheduleRepository scheduleRepository, EmployeeRepository employeeRepository,
                            AttendanceCorrectionRepository correctionRepository, AttendanceService attendanceService,
                            OvertimeSettingsService settings, HrAccessService accessService, HrAuditService audit, ObjectMapper objectMapper) {
        this.timesheetRepository = timesheetRepository;
        this.lineRepository = lineRepository;
        this.entryRepository = entryRepository;
        this.scheduleRepository = scheduleRepository;
        this.employeeRepository = employeeRepository;
        this.correctionRepository = correctionRepository;
        this.attendanceService = attendanceService;
        this.settings = settings;
        this.accessService = accessService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    private ZoneId zone() {
        return attendanceService.zone();
    }

    // ------------------------------------------------------------ generation

    /**
     * Creates (or refreshes, while still DRAFT) a timesheet for every employee based at the branch who worked or was
     * scheduled in the period. Hours at other branches are included so weekly overtime is counted once.
     */
    @Transactional
    public GenerateResult generate(UserPrincipal principal, GenerateRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.TIMESHEET_REVIEW);
        access.assertBranch(request.branchId());
        checkPeriod(request.from(), request.to());
        Instant start = request.from().atStartOfDay(zone()).toInstant();
        Instant end = request.to().plusDays(1).atStartOfDay(zone()).toInstant();
        List<EmployeeEntity> employees = employeeRepository.findByPrimaryBranchId(request.branchId()).stream()
                .filter(e -> e.getExitDate() == null || !e.getExitDate().isBefore(request.from()))
                .toList();
        int created = 0;
        int refreshed = 0;
        List<Skipped> skipped = new ArrayList<>();
        for (EmployeeEntity employee : employees) {
            List<TimeEntryEntity> entries = entryRepository.findForEmployee(employee.getId(), start, end);
            List<WorkScheduleEntity> shifts = scheduleRepository.findPublishedForEmployee(start, end, employee.getId()).stream()
                    .filter(s -> !s.getStartsAt().isBefore(start)).toList();
            if (entries.isEmpty() && shifts.isEmpty()) {
                continue;
            }
            List<TimesheetEntity> existing = timesheetRepository.findOverlapping(employee.getId(), request.from(), request.to());
            TimesheetEntity sheet = existing.stream()
                    .filter(t -> t.getPeriodStart().equals(request.from()) && t.getPeriodEnd().equals(request.to())).findFirst().orElse(null);
            if (sheet == null && !existing.isEmpty()) {
                TimesheetEntity other = existing.get(0);
                skipped.add(new Skipped(employee.getId(), employee.fullName(), "Overlaps the timesheet for " + other.getPeriodStart() + " – " + other.getPeriodEnd()));
                continue;
            }
            if (sheet != null && sheet.getStatus() != TimesheetStatus.DRAFT) {
                skipped.add(new Skipped(employee.getId(), employee.fullName(), "Already " + sheet.getStatus().name().toLowerCase()));
                continue;
            }
            boolean isNew = sheet == null;
            if (isNew) {
                sheet = new TimesheetEntity();
                sheet.setEmployeeId(employee.getId());
                sheet.setPeriodStart(request.from());
                sheet.setPeriodEnd(request.to());
            }
            sheet.setBranchId(request.branchId());
            build(sheet, entries, shifts);
            if (isNew) {
                created++;
            } else {
                refreshed++;
            }
        }
        audit.log(access.userId(), AuditAction.HR_TIMESHEET_CHANGED, "Branch", request.branchId(),
                Map.of("operation", "generated", "from", request.from().toString(), "to", request.to().toString(), "created", created, "refreshed", refreshed));
        return new GenerateResult(created, refreshed, skipped);
    }

    @Transactional
    public TimesheetDetail refresh(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.TIMESHEET_REVIEW);
        TimesheetEntity sheet = loadInScope(id, access);
        requireStatus(sheet, TimesheetStatus.DRAFT, "Only draft timesheets can be refreshed");
        rebuild(sheet);
        return detail(sheet, access);
    }

    private void rebuild(TimesheetEntity sheet) {
        Instant start = sheet.getPeriodStart().atStartOfDay(zone()).toInstant();
        Instant end = sheet.getPeriodEnd().plusDays(1).atStartOfDay(zone()).toInstant();
        build(sheet, entryRepository.findForEmployee(sheet.getEmployeeId(), start, end),
                scheduleRepository.findPublishedForEmployee(start, end, sheet.getEmployeeId()).stream()
                        .filter(s -> !s.getStartsAt().isBefore(start)).toList());
    }

    /** Recomputes every figure and replaces the lines. Only ever called while the timesheet is DRAFT. */
    private void build(TimesheetEntity sheet, List<TimeEntryEntity> entries, List<WorkScheduleEntity> shifts) {
        Instant now = Instant.now();
        List<AttendanceService.EntryView> views = attendanceService.views(entries.stream()
                .sorted(Comparator.comparing(TimeEntryEntity::getClockInAt)).toList(), now);
        Map<UUID, WorkScheduleEntity> shiftsById = shifts.stream().collect(Collectors.toMap(WorkScheduleEntity::getId, Function.identity()));
        Set<UUID> linkedShifts = views.stream().map(AttendanceService.EntryView::scheduleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<LocalDate> holidays = settings.observedHolidays(sheet.getBranchId(), sheet.getPeriodStart(), sheet.getPeriodEnd());
        Function<LocalDate, TimesheetCalculator.Rule> rules = settings.ruleResolver(sheet.getBranchId());

        List<TimesheetLineEntity> lines = new ArrayList<>();
        List<TimesheetCalculator.Piece> pieces = new ArrayList<>();
        int worked = 0, unpaid = 0, late = 0;
        for (AttendanceService.EntryView v : views) {
            LocalDate day = v.clockInAt().atZone(zone()).toLocalDate();
            WorkScheduleEntity shift = v.scheduleId() == null ? null : shiftsById.get(v.scheduleId());
            TimesheetLineEntity line = new TimesheetLineEntity();
            line.setWorkDate(day);
            line.setBranchId(v.branchId());
            line.setTimeEntryId(v.id());
            line.setScheduleId(v.scheduleId());
            line.setShiftStart(v.shiftStartsAt());
            line.setShiftEnd(v.shiftEndsAt());
            line.setClockIn(v.clockInAt());
            line.setClockOut(v.clockOutAt());
            line.setScheduledMinutes(shift == null ? 0 : scheduledPaid(shift));
            boolean open = v.clockOutAt() == null;
            line.setWorkedMinutes(open ? 0 : (int) v.metrics().elapsedMinutes());
            line.setUnpaidBreakMinutes(open ? 0 : (int) v.metrics().unpaidBreakMinutes());
            line.setPaidMinutes(open ? 0 : (int) v.metrics().paidMinutes());
            line.setLateMinutes((int) v.metrics().lateMinutes());
            line.setEarlyLeaveMinutes((int) v.metrics().earlyLeaveMinutes());
            List<String> flags = new ArrayList<>();
            if (open) flags.add("OPEN");
            if (v.unscheduled()) flags.add("UNSCHEDULED");
            if (v.editReason() != null) flags.add("CORRECTED");
            if (holidays.contains(day)) flags.add("HOLIDAY");
            TimesheetCalculator.Rule rule = rules.apply(day);
            if (rule.weekendDays() != null && rule.weekendDays().contains(day.getDayOfWeek())) flags.add("WEEKEND");
            line.setFlags(flags.isEmpty() ? null : String.join(",", flags));
            lines.add(line);
            if (!open) {
                pieces.add(new TimesheetCalculator.Piece(day, v.clockInAt(), v.clockOutAt(), line.getPaidMinutes()));
                worked += line.getWorkedMinutes();
                unpaid += line.getUnpaidBreakMinutes();
            }
            late += v.metrics().lateMinutes() > 0 ? 1 : 0;
        }
        int absences = 0;
        for (WorkScheduleEntity s : shifts) {
            if (linkedShifts.contains(s.getId()) || !s.getEndsAt().isBefore(now)) {
                continue;
            }
            TimesheetLineEntity line = new TimesheetLineEntity();
            line.setWorkDate(s.getStartsAt().atZone(zone()).toLocalDate());
            line.setBranchId(s.getBranchId());
            line.setScheduleId(s.getId());
            line.setShiftStart(s.getStartsAt());
            line.setShiftEnd(s.getEndsAt());
            line.setScheduledMinutes(scheduledPaid(s));
            line.setFlags("ABSENT");
            lines.add(line);
            absences++;
        }
        TimesheetCalculator.Result result = TimesheetCalculator.compute(pieces, rules, holidays, zone());
        sheet.setScheduledMinutes(shifts.stream().mapToInt(TimesheetService::scheduledPaid).sum());
        sheet.setWorkedMinutes(worked);
        sheet.setUnpaidBreakMinutes(unpaid);
        sheet.setPaidMinutes(result.paidMinutes());
        sheet.setRegularMinutes(result.regularMinutes());
        sheet.setDailyOvertimeMinutes(result.dailyOvertimeMinutes());
        sheet.setWeeklyOvertimeMinutes(result.weeklyOvertimeMinutes());
        sheet.setOvertimeComputedMinutes(result.overtimeMinutes());
        sheet.setOvertimeRequiresApproval(result.requiresApproval());
        sheet.setOvertimeApprovedMinutes(null);
        sheet.setHolidayMinutes(result.holidayMinutes());
        sheet.setWeekendMinutes(result.weekendMinutes());
        sheet.setNightMinutes(result.nightMinutes());
        sheet.setLateCount(late);
        sheet.setAbsenceCount(absences);
        sheet.setRuleSnapshot(snapshot(sheet, rules));
        sheet.setGeneratedAt(now);
        TimesheetEntity saved = timesheetRepository.saveAndFlush(sheet);
        lineRepository.deleteByTimesheetId(saved.getId());
        lineRepository.flush();
        lines.sort(Comparator.comparing(TimesheetLineEntity::getWorkDate)
                .thenComparing(l -> l.getClockIn() != null ? l.getClockIn() : l.getShiftStart(), Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).setTimesheetId(saved.getId());
            lines.get(i).setSortOrder(i);
        }
        lineRepository.saveAll(lines);
    }

    private static int scheduledPaid(WorkScheduleEntity s) {
        return (int) Math.max(0, Duration.between(s.getStartsAt(), s.getEndsAt()).toMinutes() - s.getBreakMinutes());
    }

    /** The rules in force on each day of the period (deduplicated), so the figures stay explainable later. */
    private String snapshot(TimesheetEntity sheet, Function<LocalDate, TimesheetCalculator.Rule> rules) {
        Map<String, TimesheetCalculator.Rule> distinct = new LinkedHashMap<>();
        for (LocalDate d = sheet.getPeriodStart(); !d.isAfter(sheet.getPeriodEnd()); d = d.plusDays(1)) {
            TimesheetCalculator.Rule r = rules.apply(d);
            distinct.putIfAbsent(r.source(), r);
        }
        try {
            return objectMapper.writeValueAsString(distinct.values());
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ workflow

    @Transactional
    public TimesheetDetail submit(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.access(principal);
        TimesheetEntity sheet = timesheetRepository.findById(id).orElseThrow(() -> new ApiException(404, "Timesheet not found"));
        boolean own = employeeRepository.findByUserId(access.userId()).map(e -> e.getId().equals(sheet.getEmployeeId())).orElse(false);
        if (!own) {
            if (!access.has(HrPermission.TIMESHEET_REVIEW) || !access.canSeeBranch(sheet.getBranchId())) {
                throw new ApiException(404, "Timesheet not found");
            }
        }
        requireStatus(sheet, TimesheetStatus.DRAFT, "Only draft timesheets can be submitted");
        rebuild(sheet); // submit what attendance says now
        List<TimesheetLineEntity> lines = lineRepository.findByTimesheetIdOrderByWorkDateAscSortOrderAsc(id);
        Set<UUID> entryIds = lines.stream().map(TimesheetLineEntity::getTimeEntryId).filter(Objects::nonNull).collect(Collectors.toSet());
        boolean pendingCorrection = correctionRepository.findByStatusOrderByCreatedAtAsc(CorrectionStatus.PENDING).stream()
                .anyMatch(c -> c.getEmployeeId().equals(sheet.getEmployeeId())
                        && (entryIds.contains(c.getTimeEntryId()) || inPeriod(sheet, c.getCorrectedClockIn())));
        if (pendingCorrection) {
            throw new ApiException(409, "An attendance correction in this period is waiting for approval");
        }
        if (lines.stream().anyMatch(l -> l.getFlags() != null && l.getFlags().contains("OPEN"))) {
            throw new ApiException(409, "A shift in this period has no clock-out yet. Clock out or correct it first.");
        }
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        sheet.setSubmittedBy(access.userId());
        sheet.setSubmittedAt(Instant.now());
        log(access, sheet, "submitted", null);
        return detail(timesheetRepository.saveAndFlush(sheet), access);
    }

    @Transactional
    public TimesheetDetail review(UserPrincipal principal, UUID id, NoteRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.TIMESHEET_REVIEW);
        TimesheetEntity sheet = loadInScope(id, access);
        requireStatus(sheet, TimesheetStatus.SUBMITTED, "Only submitted timesheets can be reviewed");
        requireNotOwn(access, sheet);
        sheet.setStatus(TimesheetStatus.REVIEWED);
        sheet.setReviewedBy(access.userId());
        sheet.setReviewedAt(Instant.now());
        appendNote(sheet, "Reviewed", request == null ? null : request.note());
        log(access, sheet, "reviewed", request == null ? null : request.note());
        return detail(timesheetRepository.saveAndFlush(sheet), access);
    }

    @Transactional
    public TimesheetDetail approve(UserPrincipal principal, UUID id, ApproveRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.TIMESHEET_REVIEW);
        TimesheetEntity sheet = loadInScope(id, access);
        requireStatus(sheet, TimesheetStatus.REVIEWED, "Review the timesheet before approving it");
        requireNotOwn(access, sheet);
        if (sheet.isOvertimeRequiresApproval() && sheet.getOvertimeComputedMinutes() > 0) {
            if (!access.has(HrPermission.OVERTIME_APPROVE)) {
                throw new ApiException(403, "This timesheet has overtime; someone who can approve overtime must approve it");
            }
            int approved = request.overtimeApprovedMinutes() != null ? request.overtimeApprovedMinutes()
                    : Math.min(sheet.getOvertimeComputedMinutes(), settings.preApprovedMinutes(sheet.getEmployeeId(), sheet.getPeriodStart(), sheet.getPeriodEnd()));
            if (approved < 0 || approved > sheet.getOvertimeComputedMinutes()) {
                throw new ApiException(400, "Approved overtime must be between 0 and " + sheet.getOvertimeComputedMinutes() + " minutes");
            }
            sheet.setOvertimeApprovedMinutes(approved);
        } else {
            sheet.setOvertimeApprovedMinutes(sheet.getOvertimeComputedMinutes());
        }
        sheet.setStatus(TimesheetStatus.APPROVED);
        sheet.setApprovedBy(access.userId());
        sheet.setApprovedAt(Instant.now());
        appendNote(sheet, "Approved", request.note());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("overtimeComputedMinutes", sheet.getOvertimeComputedMinutes());
        meta.put("overtimeApprovedMinutes", sheet.getOvertimeApprovedMinutes());
        log(access, sheet, "approved", request.note(), meta);
        return detail(timesheetRepository.saveAndFlush(sheet), access);
    }

    @Transactional
    public TimesheetDetail reopen(UserPrincipal principal, UUID id, ReasonRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.TIMESHEET_REVIEW);
        TimesheetEntity sheet = loadInScope(id, access);
        if (sheet.getStatus() == TimesheetStatus.LOCKED) {
            throw new ApiException(409, "Locked timesheets have gone to payroll and cannot be reopened; use a payroll adjustment");
        }
        if (sheet.getStatus() == TimesheetStatus.DRAFT) {
            return detail(sheet, access);
        }
        TimesheetStatus from = sheet.getStatus();
        sheet.setStatus(TimesheetStatus.DRAFT);
        sheet.setReviewedBy(null);
        sheet.setReviewedAt(null);
        sheet.setApprovedBy(null);
        sheet.setApprovedAt(null);
        sheet.setOvertimeApprovedMinutes(null);
        appendNote(sheet, "Reopened from " + from.name().toLowerCase(), request.reason());
        log(access, sheet, "reopened", request.reason());
        return detail(timesheetRepository.saveAndFlush(sheet), access);
    }

    @Transactional
    public TimesheetDetail lock(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.PAYROLL_PROCESS);
        TimesheetEntity sheet = loadInScope(id, access);
        requireStatus(sheet, TimesheetStatus.APPROVED, "Only approved timesheets can be locked");
        sheet.setStatus(TimesheetStatus.LOCKED);
        sheet.setLockedBy(access.userId());
        sheet.setLockedAt(Instant.now());
        log(access, sheet, "locked", null);
        return detail(timesheetRepository.saveAndFlush(sheet), access);
    }

    // ------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<TimesheetSummary> list(UserPrincipal principal, UUID branchId, LocalDate from, LocalDate to, TimesheetStatus status) {
        HrAccess access = accessService.requireAny(principal, HrPermission.TIMESHEET_REVIEW, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS);
        access.assertBranch(branchId);
        if (ChronoUnit.DAYS.between(from, to) > 366 || to.isBefore(from)) {
            throw new ApiException(400, "Choose a range of at most a year");
        }
        List<TimesheetEntity> rows = timesheetRepository.findAtBranches(List.of(branchId), from, to).stream()
                .filter(t -> status == null || t.getStatus() == status).toList();
        return summaries(rows);
    }

    @Transactional(readOnly = true)
    public TimesheetDetail get(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.access(principal);
        TimesheetEntity sheet = timesheetRepository.findById(id).orElseThrow(() -> new ApiException(404, "Timesheet not found"));
        boolean own = employeeRepository.findByUserId(access.userId()).map(e -> e.getId().equals(sheet.getEmployeeId())).orElse(false);
        boolean manager = (access.has(HrPermission.TIMESHEET_REVIEW) || access.has(HrPermission.PAYROLL_VIEW) || access.has(HrPermission.PAYROLL_PROCESS))
                && access.canSeeBranch(sheet.getBranchId());
        if (!own && !manager) {
            throw new ApiException(404, "Timesheet not found");
        }
        return detail(sheet, access);
    }

    @Transactional(readOnly = true)
    public List<TimesheetSummary> mine(UserPrincipal principal) {
        EmployeeEntity me = employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
        return summaries(timesheetRepository.findByEmployeeIdOrderByPeriodStartDesc(me.getId()).stream().limit(52).toList());
    }

    // ------------------------------------------------------------ helpers

    private TimesheetEntity loadInScope(UUID id, HrAccess access) {
        TimesheetEntity sheet = timesheetRepository.findById(id).orElseThrow(() -> new ApiException(404, "Timesheet not found"));
        if (!access.canSeeBranch(sheet.getBranchId())) {
            throw new ApiException(404, "Timesheet not found");
        }
        return sheet;
    }

    private void requireNotOwn(HrAccess access, TimesheetEntity sheet) {
        employeeRepository.findByUserId(access.userId()).ifPresent(me -> {
            if (me.getId().equals(sheet.getEmployeeId())) {
                throw new ApiException(403, "Someone else must review and approve your own timesheet");
            }
        });
    }

    private static void requireStatus(TimesheetEntity sheet, TimesheetStatus expected, String message) {
        if (sheet.getStatus() != expected) {
            throw new ApiException(409, message + " (this one is " + sheet.getStatus().name().toLowerCase() + ")");
        }
    }

    private boolean inPeriod(TimesheetEntity sheet, Instant at) {
        if (at == null) {
            return false;
        }
        LocalDate day = at.atZone(zone()).toLocalDate();
        return !day.isBefore(sheet.getPeriodStart()) && !day.isAfter(sheet.getPeriodEnd());
    }

    static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(400, "Choose a valid period");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new ApiException(400, "A timesheet covers at most " + MAX_PERIOD_DAYS + " days");
        }
    }

    private static void appendNote(TimesheetEntity sheet, String label, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        String line = label + ": " + note.trim();
        String existing = sheet.getNotes();
        String combined = existing == null ? line : existing + "\n" + line;
        sheet.setNotes(combined.length() > 1000 ? combined.substring(combined.length() - 1000) : combined);
    }

    private void log(HrAccess access, TimesheetEntity sheet, String operation, String note) {
        log(access, sheet, operation, note, Map.of());
    }

    private void log(HrAccess access, TimesheetEntity sheet, String operation, String note, Map<String, Object> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", operation);
        meta.put("timesheetId", sheet.getId().toString());
        meta.put("period", sheet.getPeriodStart() + " – " + sheet.getPeriodEnd());
        if (note != null && !note.isBlank()) {
            meta.put("note", note.trim());
        }
        meta.putAll(extra);
        audit.log(access.userId(), AuditAction.HR_TIMESHEET_CHANGED, "Employee", sheet.getEmployeeId(), meta);
    }

    private List<TimesheetSummary> summaries(List<TimesheetEntity> rows) {
        Map<UUID, String> names = new HashMap<>();
        employeeRepository.findByIdIn(rows.stream().map(TimesheetEntity::getEmployeeId).distinct().toList())
                .forEach(e -> names.put(e.getId(), e.fullName()));
        return rows.stream().map(t -> summary(t, names.get(t.getEmployeeId()))).toList();
    }

    private static TimesheetSummary summary(TimesheetEntity t, String name) {
        return new TimesheetSummary(t.getId(), t.getEmployeeId(), name, t.getBranchId(), t.getPeriodStart(), t.getPeriodEnd(), t.getStatus(),
                t.getScheduledMinutes(), t.getPaidMinutes(), t.getRegularMinutes(), t.getOvertimeComputedMinutes(), t.getOvertimeApprovedMinutes(),
                t.payableOvertimeMinutes(), t.isOvertimeRequiresApproval(), t.getHolidayMinutes(), t.getWeekendMinutes(), t.getNightMinutes(),
                t.getLateCount(), t.getAbsenceCount(), t.getVersion());
    }

    private TimesheetDetail detail(TimesheetEntity t, HrAccess access) {
        String name = employeeRepository.findById(t.getEmployeeId()).map(EmployeeEntity::fullName).orElse(null);
        List<LineView> lines = lineRepository.findByTimesheetIdOrderByWorkDateAscSortOrderAsc(t.getId()).stream()
                .map(l -> new LineView(l.getId(), l.getWorkDate(), l.getBranchId(), l.getTimeEntryId(), l.getScheduleId(), l.getShiftStart(),
                        l.getShiftEnd(), l.getClockIn(), l.getClockOut(), l.getScheduledMinutes(), l.getWorkedMinutes(), l.getUnpaidBreakMinutes(),
                        l.getPaidMinutes(), l.getLateMinutes(), l.getEarlyLeaveMinutes(),
                        l.getFlags() == null ? List.of() : List.of(l.getFlags().split(","))))
                .toList();
        Map<LocalDate, Integer> dailyPaid = new TreeMap<>();
        lines.forEach(l -> dailyPaid.merge(l.workDate(), l.paidMinutes(), Integer::sum));
        int preApproved = t.getOvertimeComputedMinutes() > 0
                ? settings.preApprovedMinutes(t.getEmployeeId(), t.getPeriodStart(), t.getPeriodEnd()) : 0;
        return new TimesheetDetail(summary(t, name), t.getWorkedMinutes(), t.getUnpaidBreakMinutes(), t.getDailyOvertimeMinutes(),
                t.getWeeklyOvertimeMinutes(), preApproved, t.getNotes(), t.getRuleSnapshot(), t.getGeneratedAt(), t.getSubmittedAt(),
                t.getReviewedAt(), t.getApprovedAt(), t.getLockedAt(), lines, dailyPaid);
    }

    // ------------------------------------------------------------ DTOs

    public record GenerateRequest(@NotNull UUID branchId, @NotNull LocalDate from, @NotNull LocalDate to) {}

    public record Skipped(UUID employeeId, String employeeName, String reason) {}

    public record GenerateResult(int created, int refreshed, List<Skipped> skipped) {}

    public record NoteRequest(@Size(max = 500) String note) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ApproveRequest(@Min(0) Integer overtimeApprovedMinutes, @Size(max = 500) String note) {}

    public record TimesheetSummary(UUID id, UUID employeeId, String employeeName, UUID branchId, LocalDate periodStart, LocalDate periodEnd,
                                   TimesheetStatus status, int scheduledMinutes, int paidMinutes, int regularMinutes, int overtimeComputedMinutes,
                                   Integer overtimeApprovedMinutes, int payableOvertimeMinutes, boolean overtimeRequiresApproval,
                                   int holidayMinutes, int weekendMinutes, int nightMinutes, int lateCount, int absenceCount, long version) {}

    public record LineView(UUID id, LocalDate workDate, UUID branchId, UUID timeEntryId, UUID scheduleId, Instant shiftStart, Instant shiftEnd,
                           Instant clockIn, Instant clockOut, int scheduledMinutes, int workedMinutes, int unpaidBreakMinutes, int paidMinutes,
                           int lateMinutes, int earlyLeaveMinutes, List<String> flags) {}

    public record TimesheetDetail(TimesheetSummary summary, int workedMinutes, int unpaidBreakMinutes, int dailyOvertimeMinutes,
                                  int weeklyOvertimeMinutes, int preApprovedOvertimeMinutes, String notes, String ruleSnapshot,
                                  Instant generatedAt, Instant submittedAt, Instant reviewedAt, Instant approvedAt, Instant lockedAt,
                                  List<LineView> lines, Map<LocalDate, Integer> dailyPaidMinutes) {}
}
