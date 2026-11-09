package com.restaurantmanager.core.hr.timesheet;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.hr.schedule.ShiftTemplateEntity;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Overtime rules (effective-dated), public holidays and overtime pre-authorisation requests. */
@Service
public class OvertimeSettingsService {
    private final OvertimeRuleRepository ruleRepository;
    private final PublicHolidayRepository holidayRepository;
    private final OvertimeRequestRepository requestRepository;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final TimesheetCalculator.Rule defaults;

    public OvertimeSettingsService(OvertimeRuleRepository ruleRepository, PublicHolidayRepository holidayRepository,
                                   OvertimeRequestRepository requestRepository, BranchRepository branchRepository,
                                   EmployeeRepository employeeRepository, EmployeeService employeeService,
                                   HrAccessService accessService, HrAuditService audit,
                                   @Value("${hr.overtime.default-daily-threshold-minutes:480}") int dailyThreshold,
                                   @Value("${hr.overtime.default-weekly-threshold-minutes:2880}") int weeklyThreshold,
                                   @Value("${hr.overtime.default-daily-multiplier:1.5}") BigDecimal dailyMultiplier,
                                   @Value("${hr.overtime.default-weekly-multiplier:1.5}") BigDecimal weeklyMultiplier,
                                   @Value("${hr.overtime.default-holiday-multiplier:2.0}") BigDecimal holidayMultiplier,
                                   @Value("${hr.overtime.default-requires-approval:true}") boolean requiresApproval) {
        this.ruleRepository = ruleRepository;
        this.holidayRepository = holidayRepository;
        this.requestRepository = requestRepository;
        this.branchRepository = branchRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.accessService = accessService;
        this.audit = audit;
        this.defaults = new TimesheetCalculator.Rule(dailyThreshold, weeklyThreshold, dailyMultiplier, weeklyMultiplier, Set.of(), null,
                holidayMultiplier, null, null, null, requiresApproval, "CONFIGURATION");
    }

    // ------------------------------------------------------------ rules

    /** Loads branch and organisation rules once, then answers "which rule applies on this date" without more queries. */
    @Transactional(readOnly = true)
    public java.util.function.Function<LocalDate, TimesheetCalculator.Rule> ruleResolver(UUID branchId) {
        List<OvertimeRuleEntity> branchRules = branchId == null ? List.of() : ruleRepository.findByBranchIdOrderByEffectiveFromDesc(branchId);
        List<OvertimeRuleEntity> orgRules = ruleRepository.findByBranchIdIsNullOrderByEffectiveFromDesc();
        Map<LocalDate, TimesheetCalculator.Rule> cache = new HashMap<>();
        return day -> cache.computeIfAbsent(day, d -> branchRules.stream().filter(r -> !r.getEffectiveFrom().isAfter(d)).findFirst()
                .map(r -> toRule(r, "BRANCH"))
                .orElseGet(() -> orgRules.stream().filter(r -> !r.getEffectiveFrom().isAfter(d)).findFirst()
                        .map(r -> toRule(r, "ORGANISATION")).orElse(defaults)));
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules(UserPrincipal principal, UUID branchId) {
        HrAccess access = accessService.requireAny(principal, HrPermission.TIMESHEET_REVIEW, HrPermission.OVERTIME_APPROVE, HrPermission.SETTINGS_MANAGE);
        if (branchId != null) {
            access.assertBranch(branchId);
        }
        List<OvertimeRuleEntity> rows = new java.util.ArrayList<>(ruleRepository.findByBranchIdIsNullOrderByEffectiveFromDesc());
        if (branchId != null) {
            rows.addAll(0, ruleRepository.findByBranchIdOrderByEffectiveFromDesc(branchId));
        }
        TimesheetCalculator.Rule current = ruleResolver(branchId).apply(LocalDate.now());
        List<RuleView> result = new java.util.ArrayList<>();
        // first row: the rule in force today (may come from configuration when nothing is saved)
        result.add(new RuleView(null, null, null, current.dailyThresholdMinutes(), current.weeklyThresholdMinutes(),
                current.dailyMultiplier(), current.weeklyMultiplier(), days(current.weekendDays()), current.weekendMultiplier(),
                current.holidayMultiplier(), current.nightStart(), current.nightEnd(), current.nightMultiplier(),
                current.requiresApproval(), "In force today (" + current.source().split(":")[0].toLowerCase() + ")", null));
        rows.forEach(r -> result.add(toView(r)));
        return result;
    }

    @Transactional
    public RuleView addRule(UserPrincipal principal, RuleRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (r.branchId() == null) {
            if (!access.allBranches()) {
                throw new ApiException(403, "Only central HR can change organisation overtime rules");
            }
        } else {
            access.assertBranch(r.branchId());
            if (!branchRepository.existsById(r.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        }
        if (r.dailyThresholdMinutes() == null && r.weeklyThresholdMinutes() == null) {
            throw new ApiException(400, "Set a daily threshold, a weekly threshold, or both");
        }
        if ((r.nightStart() == null) != (r.nightEnd() == null) || (r.nightStart() != null && r.nightStart().equals(r.nightEnd()))) {
            throw new ApiException(400, "Give a night window with different start and end times, or none");
        }
        Set<DayOfWeek> weekend = parseDays(r.weekendDays());
        boolean exists = (r.branchId() == null ? ruleRepository.findByBranchIdIsNullOrderByEffectiveFromDesc()
                : ruleRepository.findByBranchIdOrderByEffectiveFromDesc(r.branchId())).stream()
                .anyMatch(x -> x.getEffectiveFrom().equals(r.effectiveFrom()));
        if (exists) {
            throw new ApiException(409, "A rule already takes effect on that date; choose another date");
        }
        OvertimeRuleEntity rule = new OvertimeRuleEntity();
        rule.setBranchId(r.branchId());
        rule.setEffectiveFrom(r.effectiveFrom());
        rule.setDailyThresholdMinutes(r.dailyThresholdMinutes());
        rule.setWeeklyThresholdMinutes(r.weeklyThresholdMinutes());
        rule.setDailyMultiplier(r.dailyMultiplier());
        rule.setWeeklyMultiplier(r.weeklyMultiplier());
        rule.setWeekendDays(weekend.isEmpty() ? null : weekend.stream().sorted().map(ShiftTemplateEntity::formatDay).collect(Collectors.joining(",")));
        rule.setWeekendMultiplier(r.weekendMultiplier());
        rule.setHolidayMultiplier(r.holidayMultiplier());
        rule.setNightStart(r.nightStart());
        rule.setNightEnd(r.nightEnd());
        rule.setNightMultiplier(r.nightMultiplier());
        rule.setRequiresApproval(r.requiresApproval());
        rule.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        rule.setCreatedBy(access.userId());
        ruleRepository.save(rule);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rule", "overtime");
        meta.put("branchId", String.valueOf(r.branchId()));
        meta.put("effectiveFrom", r.effectiveFrom().toString());
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "OvertimeRule", rule.getId(), meta);
        return toView(rule);
    }

    private static TimesheetCalculator.Rule toRule(OvertimeRuleEntity r, String source) {
        return new TimesheetCalculator.Rule(r.getDailyThresholdMinutes(), r.getWeeklyThresholdMinutes(), r.getDailyMultiplier(), r.getWeeklyMultiplier(),
                parseDays(r.getWeekendDays()), r.getWeekendMultiplier(), r.getHolidayMultiplier(), r.getNightStart(), r.getNightEnd(),
                r.getNightMultiplier(), r.isRequiresApproval(), source + ":" + r.getId() + ":" + r.getEffectiveFrom());
    }

    static Set<DayOfWeek> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String d : csv.split(",")) {
            if (!d.isBlank()) {
                try {
                    days.add(ShiftTemplateEntity.parseDay(d.trim()));
                } catch (IllegalArgumentException ex) {
                    throw new ApiException(400, "Unknown day " + d.trim());
                }
            }
        }
        return days;
    }

    private static List<String> days(Set<DayOfWeek> days) {
        return days == null ? List.of() : days.stream().sorted().map(ShiftTemplateEntity::formatDay).toList();
    }

    private static RuleView toView(OvertimeRuleEntity r) {
        return new RuleView(r.getId(), r.getBranchId(), r.getEffectiveFrom(), r.getDailyThresholdMinutes(), r.getWeeklyThresholdMinutes(),
                r.getDailyMultiplier(), r.getWeeklyMultiplier(), days(parseDays(r.getWeekendDays())), r.getWeekendMultiplier(),
                r.getHolidayMultiplier(), r.getNightStart(), r.getNightEnd(), r.getNightMultiplier(), r.isRequiresApproval(), r.getNote(), r.getCreatedAt());
    }

    // ------------------------------------------------------------ holidays

    @Transactional(readOnly = true)
    public Set<LocalDate> observedHolidays(UUID branchId, LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to).stream()
                .filter(h -> h.getBranchId() == null || h.getBranchId().equals(branchId))
                .map(PublicHolidayEntity::getHolidayDate)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<HolidayView> holidays(UserPrincipal principal, int year) {
        HrAccess access = accessService.access(principal);
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .filter(h -> h.getBranchId() == null || access.canSeeBranch(h.getBranchId()))
                .map(h -> new HolidayView(h.getId(), h.getHolidayDate(), h.getName(), h.getBranchId()))
                .toList();
    }

    @Transactional
    public HolidayView addHoliday(UserPrincipal principal, HolidayRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (r.branchId() == null && !access.allBranches()) {
            throw new ApiException(403, "Only central HR can add holidays for every branch");
        }
        if (r.branchId() != null) {
            access.assertBranch(r.branchId());
        }
        boolean exists = r.branchId() == null ? holidayRepository.existsByHolidayDateAndBranchIdIsNull(r.date())
                : holidayRepository.existsByHolidayDateAndBranchId(r.date(), r.branchId());
        if (exists) {
            throw new ApiException(409, "That date is already a holiday");
        }
        PublicHolidayEntity h = new PublicHolidayEntity();
        h.setHolidayDate(r.date());
        h.setName(r.name().trim());
        h.setBranchId(r.branchId());
        h.setCreatedBy(access.userId());
        holidayRepository.save(h);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "PublicHoliday", h.getId(), Map.of("date", r.date().toString(), "name", h.getName()));
        return new HolidayView(h.getId(), h.getHolidayDate(), h.getName(), h.getBranchId());
    }

    @Transactional
    public void deleteHoliday(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        PublicHolidayEntity h = holidayRepository.findById(id).orElseThrow(() -> new ApiException(404, "Holiday not found"));
        if (h.getBranchId() == null ? !access.allBranches() : !access.canSeeBranch(h.getBranchId())) {
            throw new ApiException(403, "You cannot remove this holiday");
        }
        holidayRepository.delete(h);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "PublicHoliday", id, Map.of("operation", "removed", "date", h.getHolidayDate().toString()));
    }

    // ------------------------------------------------------------ overtime pre-authorisation

    @Transactional(readOnly = true)
    public List<OvertimeRequestView> requests(UserPrincipal principal, OvertimeRequestStatus status) {
        HrAccess access = accessService.requireAny(principal, HrPermission.OVERTIME_APPROVE, HrPermission.TIMESHEET_REVIEW, HrPermission.ATTENDANCE_MANAGE);
        List<OvertimeRequestEntity> rows = status == null ? requestRepository.findTop200ByOrderByCreatedAtDesc() : requestRepository.findByStatusOrderByCreatedAtAsc(status);
        return views(rows.stream().filter(r -> access.canSeeBranch(r.getBranchId())).toList());
    }

    @Transactional
    public OvertimeRequestView request(UserPrincipal principal, OvertimeRequestInput r) {
        HrAccess access = accessService.requireAny(principal, HrPermission.ATTENDANCE_MANAGE, HrPermission.SCHEDULE_MANAGE, HrPermission.TIMESHEET_REVIEW);
        EmployeeEntity employee = employeeService.loadVisible(r.employeeId(), access);
        UUID branchId = r.branchId() != null ? r.branchId() : employee.primaryBranchId();
        access.assertBranch(branchId);
        OvertimeRequestEntity req = new OvertimeRequestEntity();
        req.setEmployeeId(employee.getId());
        req.setBranchId(branchId);
        req.setWorkDate(r.workDate());
        req.setMinutes(r.minutes());
        req.setReason(r.reason().trim());
        req.setRequestedBy(access.userId());
        requestRepository.save(req);
        audit.log(access.userId(), AuditAction.HR_OVERTIME_DECIDED, "Employee", employee.getId(),
                Map.of("operation", "requested", "requestId", req.getId().toString(), "workDate", r.workDate().toString(), "minutes", r.minutes()));
        return views(List.of(req)).get(0);
    }

    @Transactional
    public OvertimeRequestView decide(UserPrincipal principal, UUID id, Decision decision) {
        HrAccess access = accessService.require(principal, HrPermission.OVERTIME_APPROVE);
        OvertimeRequestEntity req = requestRepository.findById(id).orElseThrow(() -> new ApiException(404, "Overtime request not found"));
        if (!access.canSeeBranch(req.getBranchId())) {
            throw new ApiException(404, "Overtime request not found");
        }
        if (req.getStatus() != OvertimeRequestStatus.PENDING) {
            throw new ApiException(409, "This request has already been decided");
        }
        if (access.userId().equals(req.getRequestedBy())) {
            throw new ApiException(403, "Someone else must approve overtime you requested");
        }
        employeeRepository.findByUserId(access.userId()).ifPresent(me -> {
            if (me.getId().equals(req.getEmployeeId())) {
                throw new ApiException(403, "You cannot approve your own overtime");
            }
        });
        req.setStatus(decision.approve() ? OvertimeRequestStatus.APPROVED : OvertimeRequestStatus.REJECTED);
        req.setDecidedBy(access.userId());
        req.setDecidedAt(Instant.now());
        req.setDecisionNote(decision.note() == null || decision.note().isBlank() ? null : decision.note().trim());
        audit.log(access.userId(), AuditAction.HR_OVERTIME_DECIDED, "Employee", req.getEmployeeId(),
                Map.of("operation", req.getStatus().name().toLowerCase(), "requestId", id.toString(), "minutes", req.getMinutes()));
        return views(List.of(req)).get(0);
    }

    /** Approved pre-authorised minutes for an employee within a period. */
    @Transactional(readOnly = true)
    public int preApprovedMinutes(UUID employeeId, LocalDate from, LocalDate to) {
        return requestRepository.findByEmployeeIdAndStatusAndWorkDateBetween(employeeId, OvertimeRequestStatus.APPROVED, from, to).stream()
                .mapToInt(OvertimeRequestEntity::getMinutes).sum();
    }

    private List<OvertimeRequestView> views(List<OvertimeRequestEntity> rows) {
        Map<UUID, String> names = new HashMap<>();
        employeeRepository.findByIdIn(rows.stream().map(OvertimeRequestEntity::getEmployeeId).distinct().toList())
                .forEach(e -> names.put(e.getId(), e.fullName()));
        return rows.stream().map(r -> new OvertimeRequestView(r.getId(), r.getEmployeeId(), names.get(r.getEmployeeId()), r.getBranchId(),
                r.getWorkDate(), r.getMinutes(), r.getReason(), r.getStatus(), r.getDecisionNote(), r.getDecidedAt(), r.getCreatedAt())).toList();
    }

    // ------------------------------------------------------------ DTOs

    public record RuleRequest(UUID branchId, @NotNull LocalDate effectiveFrom,
                              @Min(60) @Max(1440) Integer dailyThresholdMinutes, @Min(60) @Max(10080) Integer weeklyThresholdMinutes,
                              @NotNull @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal dailyMultiplier,
                              @NotNull @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal weeklyMultiplier,
                              @Size(max = 30) String weekendDays,
                              @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal weekendMultiplier,
                              @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal holidayMultiplier,
                              LocalTime nightStart, LocalTime nightEnd,
                              @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal nightMultiplier,
                              boolean requiresApproval, @Size(max = 300) String note) {}

    public record RuleView(UUID id, UUID branchId, LocalDate effectiveFrom, Integer dailyThresholdMinutes, Integer weeklyThresholdMinutes,
                           BigDecimal dailyMultiplier, BigDecimal weeklyMultiplier, List<String> weekendDays, BigDecimal weekendMultiplier,
                           BigDecimal holidayMultiplier, LocalTime nightStart, LocalTime nightEnd, BigDecimal nightMultiplier,
                           boolean requiresApproval, String note, Instant createdAt) {}

    public record HolidayRequest(@NotNull LocalDate date, @NotBlank @Size(max = 120) String name, UUID branchId) {}

    public record HolidayView(UUID id, LocalDate date, String name, UUID branchId) {}

    public record OvertimeRequestInput(@NotNull UUID employeeId, UUID branchId, @NotNull LocalDate workDate,
                                       @Min(15) @Max(960) int minutes, @NotBlank @Size(max = 500) String reason) {}

    public record Decision(boolean approve, @Size(max = 500) String note) {}

    public record OvertimeRequestView(UUID id, UUID employeeId, String employeeName, UUID branchId, LocalDate workDate, int minutes,
                                      String reason, OvertimeRequestStatus status, String decisionNote, Instant decidedAt, Instant createdAt) {}
}
