package com.restaurantmanager.core.hr.report;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.leave.LeaveRequestEntity;
import com.restaurantmanager.core.hr.leave.LeaveRequestRepository;
import com.restaurantmanager.core.hr.leave.LeaveStatus;
import com.restaurantmanager.core.hr.leave.LeaveTypeEntity;
import com.restaurantmanager.core.hr.leave.LeaveTypeRepository;
import com.restaurantmanager.core.hr.payroll.PayrollRunEntity;
import com.restaurantmanager.core.hr.payroll.PayrollRunRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tabular HR reports for the viewer's branches, shown on screen or downloaded as CSV. Each report needs hr.reports.view plus
 * the permission behind its data, and only the payroll report carries pay figures.
 */
@Service
public class HrReportService {
    static final int MAX_PERIOD_DAYS = 366;
    private static final String CSV_FORMULA_START = "=+-@\t\r";

    public enum ReportType {
        EMPLOYEES("Employee register", "Everyone in your branches with department, position, branch, type, status and dates", false,
                HrPermission.EMPLOYEE_VIEW),
        MOVEMENT("Joiners and leavers", "People who started or left in the period, with how they left", true, HrPermission.EMPLOYEE_VIEW),
        LEAVE("Leave", "Leave requests overlapping the period, with type, days and status (reasons are not exported)", true,
                HrPermission.LEAVE_APPROVE, HrPermission.LEAVE_MANAGE),
        HOURS("Hours and attendance", "Timesheets overlapping the period: scheduled, paid and overtime hours, lateness and absences", true,
                HrPermission.TIMESHEET_REVIEW, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS),
        PAYROLL("Payroll summary", "Payroll runs in the period with gross, deductions, net and employer cost", true,
                HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE);

        final String title;
        final String description;
        final boolean periodic;
        final HrPermission[] anyOf;

        ReportType(String title, String description, boolean periodic, HrPermission... anyOf) {
            this.title = title;
            this.description = description;
            this.periodic = periodic;
            this.anyOf = anyOf;
        }

        boolean allowedFor(HrAccess access) {
            return access.has(HrPermission.REPORTS_VIEW) && Arrays.stream(anyOf).anyMatch(access::has);
        }
    }

    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final TimesheetRepository timesheetRepository;
    private final PayrollRunRepository runRepository;
    private final ZoneId zone;

    public HrReportService(HrAccessService accessService, HrAuditService audit, BranchRepository branchRepository, EmployeeRepository employeeRepository,
                           LeaveRequestRepository leaveRepository, LeaveTypeRepository leaveTypeRepository, TimesheetRepository timesheetRepository,
                           PayrollRunRepository runRepository, @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.accessService = accessService;
        this.audit = audit;
        this.branchRepository = branchRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.timesheetRepository = timesheetRepository;
        this.runRepository = runRepository;
        this.zone = ZoneId.of(timeZone);
    }

    /** The reports this viewer may run. */
    @Transactional(readOnly = true)
    public List<ReportInfo> available(UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.REPORTS_VIEW);
        return Arrays.stream(ReportType.values()).filter(t -> t.allowedFor(access))
                .map(t -> new ReportInfo(t, t.title, t.description, t.periodic))
                .toList();
    }

    @Transactional(readOnly = true)
    public Report run(UserPrincipal principal, ReportType type, ReportQuery query) {
        HrAccess access = accessService.require(principal, HrPermission.REPORTS_VIEW);
        if (!type.allowedFor(access)) {
            throw new ApiException(403, "You do not have permission for this report");
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate from = query.from() != null ? query.from() : today.withDayOfMonth(1);
        LocalDate to = query.to() != null ? query.to() : today;
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new ApiException(400, "Choose a period of 1 to " + MAX_PERIOD_DAYS + " days");
        }
        if (query.branchId() != null) {
            access.assertBranch(query.branchId());
        }
        Map<UUID, String> branches = branchRepository.findAll().stream().filter(b -> access.canSeeBranch(b.getId()))
                .collect(Collectors.toMap(BranchEntity::getId, BranchEntity::getName));
        Set<UUID> scope = query.branchId() != null ? Set.of(query.branchId()) : branches.keySet();
        boolean includeCentral = query.branchId() == null && access.allBranches();
        Scope s = new Scope(scope, includeCentral, branches);

        Table table = switch (type) {
            case EMPLOYEES -> employees(s, query.includeExited());
            case MOVEMENT -> movement(s, from, to);
            case LEAVE -> leave(s, from, to);
            case HOURS -> hours(s, from, to);
            case PAYROLL -> payroll(s, from, to);
        };
        return new Report(type, type.title, type.periodic ? from : null, type.periodic ? to : null, query.branchId(),
                query.branchId() == null ? null : branches.get(query.branchId()), table.columns(), table.rows());
    }

    /** CSV (UTF-8 with BOM so spreadsheets read accents), audited as an export (so not a read-only transaction). */
    @Transactional
    public byte[] csv(UserPrincipal principal, ReportType type, ReportQuery query) {
        Report report = run(principal, type, query);
        StringBuilder out = new StringBuilder("﻿");
        out.append(report.columns().stream().map(c -> cell(c.label())).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : report.rows()) {
            out.append(report.columns().stream().map(c -> cell(row.get(c.key()))).collect(Collectors.joining(","))).append("\r\n");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("report", type.name());
        meta.put("rows", report.rows().size());
        if (report.from() != null) {
            meta.put("from", report.from().toString());
            meta.put("to", report.to().toString());
        }
        if (report.branchId() != null) {
            meta.put("branchId", report.branchId().toString());
        }
        audit.log(principal.userId(), AuditAction.HR_REPORT_EXPORTED, "Report", null, meta);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** One CSV cell: quoted when needed, and text that a spreadsheet would run as a formula is prefixed with an apostrophe. */
    static String cell(Object value) {
        if (value == null) return "";
        String text = value instanceof BigDecimal d ? d.toPlainString() : value.toString();
        if (!(value instanceof Number) && !text.isEmpty() && CSV_FORMULA_START.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r") || text.startsWith("'")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    // ---------------------------------------------------------------- reports

    private Table employees(Scope s, boolean includeExited) {
        List<EmployeeEntity> people = employeesInScope(s).stream()
                .filter(e -> includeExited || !e.getEmploymentStatus().isExit())
                .sorted(Comparator.comparing(EmployeeEntity::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmployeeEntity e : people) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("employeeNumber", e.getEmployeeNumber());
            r.put("name", e.fullName());
            r.put("department", e.getDepartment() == null ? null : e.getDepartment().getName());
            r.put("position", e.getPosition() == null ? null : e.getPosition().getTitle());
            r.put("branch", s.branchName(e.primaryBranchId()));
            r.put("employmentType", e.getEmploymentType().name());
            r.put("status", e.getEmploymentStatus().name());
            r.put("hireDate", e.getHireDate());
            r.put("exitDate", e.getExitDate());
            r.put("phone", e.getPhone());
            r.put("email", e.getEmail());
            rows.add(r);
        }
        return new Table(List.of(col("employeeNumber", "Employee no."), col("name", "Name"), col("department", "Department"),
                col("position", "Position"), col("branch", "Branch"), col("employmentType", "Employment type"), col("status", "Status"),
                col("hireDate", "Hire date"), col("exitDate", "Exit date"), col("phone", "Phone"), col("email", "Email")), rows);
    }

    private Table movement(Scope s, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmployeeEntity e : employeesInScope(s)) {
            if (!e.getHireDate().isBefore(from) && !e.getHireDate().isAfter(to)) {
                rows.add(movementRow("Joined", e.getHireDate(), e, s, e.getEmploymentType().name()));
            }
            if (e.getExitDate() != null && !e.getExitDate().isBefore(from) && !e.getExitDate().isAfter(to)) {
                rows.add(movementRow("Left", e.getExitDate(), e, s, e.getEmploymentStatus().name()
                        + (e.getExitReason() == null ? "" : ": " + e.getExitReason())));
            }
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDate) r.get("date")).thenComparing(r -> (String) r.get("name")));
        return new Table(List.of(col("event", "Event"), col("date", "Date"), col("employeeNumber", "Employee no."), col("name", "Name"),
                col("position", "Position"), col("branch", "Branch"), col("detail", "Detail")), rows);
    }

    private static Map<String, Object> movementRow(String event, LocalDate date, EmployeeEntity e, Scope s, String detail) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("event", event);
        r.put("date", date);
        r.put("employeeNumber", e.getEmployeeNumber());
        r.put("name", e.fullName());
        r.put("position", e.getPosition() == null ? null : e.getPosition().getTitle());
        r.put("branch", s.branchName(e.primaryBranchId()));
        r.put("detail", detail);
        return r;
    }

    private Table leave(Scope s, LocalDate from, LocalDate to) {
        Set<LeaveStatus> all = EnumSet.allOf(LeaveStatus.class);
        List<LeaveRequestEntity> requests = s.scope().isEmpty() ? List.of() : leaveRepository.findAtBranches(s.scope(), all, from, to);
        if (s.includeCentral()) {
            Set<UUID> seen = requests.stream().map(LeaveRequestEntity::getId).collect(Collectors.toSet());
            requests = new ArrayList<>(requests);
            for (LeaveRequestEntity r : leaveRepository.findAllBranches(all, from, to)) {
                if (r.getBranchId() == null && seen.add(r.getId())) requests.add(r);
            }
        }
        Map<UUID, EmployeeEntity> people = byId(requests.stream().map(LeaveRequestEntity::getEmployeeId).toList());
        Map<UUID, String> types = leaveTypeRepository.findAll().stream().collect(Collectors.toMap(LeaveTypeEntity::getId, LeaveTypeEntity::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveRequestEntity q : requests) {
            EmployeeEntity e = people.get(q.getEmployeeId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("employeeNumber", e == null ? null : e.getEmployeeNumber());
            r.put("name", e == null ? null : e.fullName());
            r.put("branch", s.branchName(q.getBranchId()));
            r.put("leaveType", types.get(q.getLeaveTypeId()));
            r.put("startDate", q.getStartDate());
            r.put("endDate", q.getEndDate());
            r.put("days", q.getDays());
            r.put("status", q.getStatus().name());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDate) r.get("startDate")).thenComparing(r -> String.valueOf(r.get("name"))));
        return new Table(List.of(col("employeeNumber", "Employee no."), col("name", "Name"), col("branch", "Branch"), col("leaveType", "Leave type"),
                col("startDate", "From"), col("endDate", "To"), col("days", "Days"), col("status", "Status")), rows);
    }

    private Table hours(Scope s, LocalDate from, LocalDate to) {
        List<TimesheetEntity> sheets = s.scope().isEmpty() ? List.of() : timesheetRepository.findAtBranches(s.scope(), from, to);
        Map<UUID, EmployeeEntity> people = byId(sheets.stream().map(TimesheetEntity::getEmployeeId).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TimesheetEntity t : sheets) {
            EmployeeEntity e = people.get(t.getEmployeeId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("employeeNumber", e == null ? null : e.getEmployeeNumber());
            r.put("name", e == null ? null : e.fullName());
            r.put("branch", s.branchName(t.getBranchId()));
            r.put("periodStart", t.getPeriodStart());
            r.put("periodEnd", t.getPeriodEnd());
            r.put("status", t.getStatus().name());
            r.put("scheduledHours", hoursOf(t.getScheduledMinutes()));
            r.put("paidHours", hoursOf(t.getPaidMinutes()));
            r.put("overtimeHours", hoursOf(t.getOvertimeComputedMinutes()));
            r.put("payableOvertimeHours", hoursOf(t.payableOvertimeMinutes()));
            r.put("lateCount", t.getLateCount());
            r.put("absences", t.getAbsenceCount());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("name"))).thenComparing(r -> (LocalDate) r.get("periodStart")));
        return new Table(List.of(col("employeeNumber", "Employee no."), col("name", "Name"), col("branch", "Branch"), col("periodStart", "From"),
                col("periodEnd", "To"), col("status", "Status"), col("scheduledHours", "Scheduled h"), col("paidHours", "Paid h"),
                col("overtimeHours", "Overtime h"), col("payableOvertimeHours", "Payable overtime h"), col("lateCount", "Late"),
                col("absences", "Absences")), rows);
    }

    private Table payroll(Scope s, LocalDate from, LocalDate to) {
        List<PayrollRunEntity> runs = s.scope().isEmpty() ? List.of() : runRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(s.scope()).stream()
                .filter(r -> !r.getPeriodStart().isAfter(to) && !r.getPeriodEnd().isBefore(from))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PayrollRunEntity run : runs) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("runNumber", run.getRunNumber());
            r.put("branch", s.branchName(run.getBranchId()));
            r.put("periodStart", run.getPeriodStart());
            r.put("periodEnd", run.getPeriodEnd());
            r.put("payDate", run.getPayDate());
            r.put("status", run.getStatus().name());
            r.put("employees", run.getEmployeeCount());
            r.put("currency", run.getCurrency());
            r.put("gross", run.getGrossTotal());
            r.put("deductions", run.getDeductionsTotal());
            r.put("net", run.getNetTotal());
            r.put("employerCost", run.getEmployerTotal());
            rows.add(r);
        }
        return new Table(List.of(col("runNumber", "Run"), col("branch", "Branch"), col("periodStart", "From"), col("periodEnd", "To"),
                col("payDate", "Pay date"), col("status", "Status"), col("employees", "Employees"), col("currency", "Currency"),
                col("gross", "Gross"), col("deductions", "Deductions"), col("net", "Net"), col("employerCost", "Employer cost")), rows);
    }

    // ---------------------------------------------------------------- helpers

    private List<EmployeeEntity> employeesInScope(Scope s) {
        return employeeRepository.findAll().stream()
                .filter(e -> e.primaryBranchId() == null ? s.includeCentral() : s.scope().contains(e.primaryBranchId()))
                .toList();
    }

    private Map<UUID, EmployeeEntity> byId(List<UUID> ids) {
        return employeeRepository.findByIdIn(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
    }

    private static BigDecimal hoursOf(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static Column col(String key, String label) {
        return new Column(key, label);
    }

    private record Scope(Set<UUID> scope, boolean includeCentral, Map<UUID, String> branches) {
        String branchName(UUID id) {
            return id == null ? "Central" : branches.get(id);
        }
    }

    private record Table(List<Column> columns, List<Map<String, Object>> rows) {}

    public record ReportQuery(LocalDate from, LocalDate to, UUID branchId, boolean includeExited) {}

    public record ReportInfo(ReportType type, String title, String description, boolean periodic) {}

    public record Column(String key, String label) {}

    public record Report(ReportType type, String title, LocalDate from, LocalDate to, UUID branchId, String branchName, List<Column> columns,
                         List<Map<String, Object>> rows) {}
}
