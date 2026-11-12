package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.timesheet.TimesheetEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrPayrollIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    @Autowired
    private TimesheetRepository timesheetRepository;
    @Autowired
    private TimesheetLineRepository timesheetLineRepository;

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity clerk;
    private UserEntity chefUser;
    private UUID chef;
    private UUID casual;
    private LocalDate monthStart;
    private LocalDate monthEnd;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        clerk = staff("Payroll Clerk", Role.CASHIER, accra);
        grant(clerk, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE, HrPermission.EMPLOYEE_VIEW);
        chefUser = staff("Kwesi Chef", Role.CASHIER, accra);
        chef = createEmployee(admin, "Kwesi", "Chef", accra);
        postJson("/hr/employees/" + chef + "/link-user", Map.of("userId", chefUser.getId()), admin).andExpect(status().isOk());
        casual = createEmployee(admin, "Afia", "Casual", accra);
        contract(chef, "PERMANENT", "MONTHLY", "3000.00");
        contract(casual, "PART_TIME", "HOURLY", "20.00");
        monthStart = LocalDate.now(ZONE).minusMonths(1).withDayOfMonth(1);
        monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
    }

    // ------------------------------------------------------------ helpers

    private void contract(UUID employee, String type, String salaryType, String amount) throws Exception {
        String id = json(postJson("/hr/employees/" + employee + "/contracts", Map.of("contractType", type, "startDate", "2025-01-15",
                "salaryType", salaryType, "salaryAmount", amount, "weeklyHours", 40), admin).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + id + "/activate", Map.of(), admin).andExpect(status().isOk());
    }

    private UUID timesheet(UUID employee, LocalDate from, TimesheetStatus status, int paid, int overtime, int approvedOvertime) {
        TimesheetEntity t = new TimesheetEntity();
        t.setEmployeeId(employee);
        t.setBranchId(accra.getId());
        t.setPeriodStart(from);
        t.setPeriodEnd(from.plusDays(6));
        t.setStatus(status);
        t.setPaidMinutes(paid);
        t.setRegularMinutes(paid - overtime);
        t.setDailyOvertimeMinutes(overtime);
        t.setOvertimeComputedMinutes(overtime);
        t.setOvertimeApprovedMinutes(approvedOvertime);
        t.setGeneratedAt(Instant.now());
        UUID id = timesheetRepository.save(t).getId();
        TimesheetLineEntity line = new TimesheetLineEntity();
        line.setTimesheetId(id);
        line.setWorkDate(from);
        line.setPaidMinutes(paid);
        timesheetLineRepository.save(line);
        return id;
    }

    private void rule(Map<String, Object> body) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>(body);
        b.putIfAbsent("effectiveFrom", "2020-01-01");
        postJson("/hr/payroll-rules", b, admin).andExpect(status().isCreated());
    }

    private String createRun(UserEntity actor) throws Exception {
        return json(postJson("/hr/payroll-runs", Map.of("branchId", accra.getId(), "periodStart", monthStart.toString(), "periodEnd", monthEnd.toString(),
                "payDate", monthEnd.toString()), actor).andExpect(status().isCreated())).at("/summary/id").asText();
    }

    private JsonNode payslipOf(JsonNode run, UUID employee) {
        for (JsonNode p : run.get("payslips")) {
            if (p.get("employeeId").asText().equals(employee.toString())) return p;
        }
        throw new AssertionError("No payslip for " + employee);
    }

    // ------------------------------------------------------------ tests

    @Test
    void fullCycle_calculateApproveFinalizePay_withRulesAndSelfService() throws Exception {
        rule(Map.of("code", "PENSION", "name", "Pension", "kind", "EMPLOYEE_DEDUCTION", "method", "PERCENT", "base", "BASIC", "ratePercent", 5.5, "preTax", true));
        rule(Map.of("code", "TAX", "name", "Income tax", "kind", "EMPLOYEE_DEDUCTION", "method", "BRACKETS", "base", "TAXABLE",
                "brackets", List.of(Map.of("upTo", 490, "ratePercent", 0), new HashMap<>(Map.of("ratePercent", 10)))));
        rule(Map.of("code", "ER_PENSION", "name", "Employer pension", "kind", "EMPLOYER_CONTRIBUTION", "method", "PERCENT", "base", "BASIC", "ratePercent", 13));
        UUID sheet = timesheet(casual, monthStart, TimesheetStatus.LOCKED, 600, 120, 60);

        String id = createRun(clerk);
        JsonNode run = json(postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk).andExpect(status().isOk()));
        assertThat(run.at("/summary/status").asText()).isEqualTo("CALCULATED");
        assertThat(run.at("/summary/runNumber").asText()).matches("PAY-[0-9]{6}");
        assertThat(run.at("/summary/employeeCount").asInt()).isEqualTo(2);

        JsonNode chefSlip = payslipOf(run, chef);
        assertThat(chefSlip.get("gross").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(chefSlip.get("taxable").decimalValue()).isEqualByComparingTo("2835.00");        // − 165 pension
        assertThat(chefSlip.get("employeeDeductions").decimalValue()).isEqualByComparingTo("399.50"); // 165 + (2835 − 490) × 10%
        assertThat(chefSlip.get("net").decimalValue()).isEqualByComparingTo("2600.50");
        assertThat(chefSlip.get("employerContributions").decimalValue()).isEqualByComparingTo("390.00");

        JsonNode casualSlip = payslipOf(run, casual);
        // 540 normal minutes at 20/h + 60 approved overtime minutes at 30/h (configured default ×1.5); 60 unapproved minutes paid at normal rate
        assertThat(casualSlip.get("basic").decimalValue()).isEqualByComparingTo("180.00");
        assertThat(casualSlip.get("gross").decimalValue()).isEqualByComparingTo("210.00");
        assertThat(casualSlip.get("workedDays").asInt()).isEqualTo(1);
        assertThat(timesheetRepository.findById(sheet).orElseThrow().getPayrollRunId()).hasToString(id);

        // the employee cannot see a payslip before it is finalized
        getJson("/hr/payslips/" + chefSlip.get("id").asText(), chefUser).andExpect(status().isNotFound());
        assertThat(json(getJson("/hr/me/payslips", chefUser))).isEmpty();

        postJson("/hr/payroll-runs/" + id + "/approve", Map.of(), clerk)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Someone other than the person who calculated this run must approve it"));
        postJson("/hr/payroll-runs/" + id + "/finalize", Map.of(), admin).andExpect(status().isConflict());
        postJson("/hr/payroll-runs/" + id + "/approve", Map.of(), admin).andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("APPROVED"));
        postJson("/hr/payroll-runs/" + id + "/adjustments", Map.of("employeeId", chef, "kind", "EARNING", "description", "Late bonus", "amount", 50), clerk)
                .andExpect(status().isConflict());
        postJson("/hr/payroll-runs/" + id + "/finalize", Map.of(), clerk).andExpect(status().isForbidden());
        postJson("/hr/payroll-runs/" + id + "/finalize", Map.of(), admin).andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("FINALIZED"));
        postJson("/hr/payroll-runs/" + id + "/cancel", Map.of("reason", "Mistake"), admin).andExpect(status().isConflict());

        JsonNode mine = json(getJson("/hr/me/payslips", chefUser).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("net").decimalValue()).isEqualByComparingTo("2600.50");
        JsonNode detail = json(getJson("/hr/payslips/" + chefSlip.get("id").asText(), chefUser).andExpect(status().isOk()));
        assertThat(detail.get("lines").findValuesAsText("code")).contains("BASIC", "PENSION", "TAX", "ER_PENSION");

        postJson("/hr/payroll-runs/" + id + "/pay", Map.of("method", "BANK", "payslipIds", List.of(chefSlip.get("id").asText())), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("FINALIZED")).andExpect(jsonPath("$.summary.paidCount").value(1));
        postJson("/hr/payroll-runs/" + id + "/pay", Map.of("method", "MOBILE_MONEY", "reference", "MOMO-123"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("PAID"));

        assertThat(auditLogRepository.findByAction(AuditAction.HR_PAYROLL_CHANGED))
                .hasSizeGreaterThanOrEqualTo(5)
                .noneMatch(a -> a.getMetadata() != null && (a.getMetadata().contains("3000") || a.getMetadata().contains("2600")));
    }

    @Test
    void blockingIssues_adjustmentsSendRunBackToDraft_overlapAndCancel() throws Exception {
        UUID helper = createEmployee(admin, "Kojo", "Helper", accra);
        contract(helper, "PART_TIME", "HOURLY", "15.00");
        UUID pending = timesheet(helper, monthStart, TimesheetStatus.SUBMITTED, 480, 0, 0);
        UUID locked = timesheet(casual, monthStart.plusDays(7), TimesheetStatus.LOCKED, 480, 0, 0);

        String id = createRun(clerk);
        JsonNode run = json(postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk));
        assertThat(payslipOf(run, helper).get("issues").get(0).asText()).isEqualTo("UNLOCKED_TIMESHEETS");
        assertThat(run.get("blockingPayslips").asInt()).isEqualTo(1);
        postJson("/hr/payroll-runs/" + id + "/approve", Map.of(), admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("1 payslip has problems to fix before approval"));

        // fix: the timesheet gets locked, a bonus is added -> the run must be recalculated before approval
        TimesheetEntity t = timesheetRepository.findById(pending).orElseThrow();
        t.setStatus(TimesheetStatus.LOCKED);
        timesheetRepository.save(t);
        postJson("/hr/payroll-runs/" + id + "/adjustments",
                Map.of("employeeId", chef, "kind", "EARNING", "taxable", true, "description", "Christmas bonus", "amount", 200), clerk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("DRAFT"));
        postJson("/hr/payroll-runs/" + id + "/approve", Map.of(), admin).andExpect(status().isConflict());
        run = json(postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk));
        assertThat(run.get("blockingPayslips").asInt()).isZero();
        assertThat(payslipOf(run, chef).get("gross").decimalValue()).isEqualByComparingTo("3200.00");
        assertThat(payslipOf(run, helper).get("gross").decimalValue()).isEqualByComparingTo("120.00");

        // one run per branch and period
        postJson("/hr/payroll-runs", Map.of("branchId", accra.getId(), "periodStart", monthEnd.toString(), "periodEnd", monthEnd.toString()), clerk)
                .andExpect(status().isConflict());
        // a manager has no payroll access by default
        getJson("/hr/payroll-runs", manager).andExpect(status().isForbidden());

        postJson("/hr/payroll-runs/" + id + "/cancel", Map.of("reason", "Wrong period"), clerk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("CANCELLED"));
        assertThat(timesheetRepository.findById(locked).orElseThrow().getPayrollRunId()).isNull();
        // after cancelling, the period can be run again and the timesheets are available
        String again = createRun(clerk);
        postJson("/hr/payroll-runs/" + again + "/calculate", Map.of(), clerk).andExpect(jsonPath("$.summary.employeeCount").value(3));
    }

    @Test
    void newJoinerIsProRated_unpaidLeaveDeducted_andEmployeesWithoutContractSkipped() throws Exception {
        LocalDate hired = monthStart.plusDays(10);
        Map<String, Object> body = new HashMap<>(employeeBody("Ama", "Joiner", accra));
        body.put("hireDate", hired.toString());
        UUID joiner = UUID.fromString(json(postJson("/hr/employees", body, admin).andExpect(status().isCreated())).at("/summary/id").asText());
        String cid = json(postJson("/hr/employees/" + joiner + "/contracts", Map.of("contractType", "PERMANENT", "startDate", hired.toString(),
                "salaryType", "MONTHLY", "salaryAmount", "3100.00", "weeklyHours", 40), admin).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + cid + "/activate", Map.of(), admin).andExpect(status().isOk());
        UUID noContract = createEmployee(admin, "Yaw", "Nocontract", accra);

        Map<String, Object> unpaid = new LinkedHashMap<>();
        unpaid.put("code", "UNPAID");
        unpaid.put("name", "Unpaid leave");
        unpaid.put("paid", false);
        unpaid.put("deductsBalance", false);
        unpaid.put("countBasis", "WORKDAYS");
        unpaid.put("approvalSteps", List.of("BRANCH_MANAGER"));
        String unpaidType = json(postJson("/hr/leave-types", unpaid, admin).andExpect(status().isCreated())).get("id").asText();
        LocalDate day = monthStart.plusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) day = day.plusDays(1);
        postJson("/hr/leave-requests", Map.of("employeeId", chef, "leaveTypeId", unpaidType, "startDate", day.toString(), "endDate", day.toString(),
                "recordApproved", true), admin).andExpect(status().isCreated());

        String id = createRun(clerk);
        JsonNode run = json(postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk));
        JsonNode joinerSlip = payslipOf(run, joiner);
        long employedDays = java.time.temporal.ChronoUnit.DAYS.between(hired, monthEnd) + 1;
        BigDecimal expected = new BigDecimal("3100.00").multiply(BigDecimal.valueOf(employedDays))
                .divide(BigDecimal.valueOf(monthStart.lengthOfMonth()), 2, java.math.RoundingMode.HALF_UP);
        assertThat(joinerSlip.get("gross").decimalValue()).isEqualByComparingTo(expected);
        assertThat(joinerSlip.get("issues").get(0).asText()).isEqualTo("PARTIAL_PERIOD");

        JsonNode chefSlip = payslipOf(run, chef);
        assertThat(chefSlip.get("unpaidLeaveDays").decimalValue()).isEqualByComparingTo("1");
        assertThat(chefSlip.get("gross").decimalValue()).isEqualByComparingTo("2861.54"); // 3000 − 3000 × 12 / 260

        assertThat(run.get("skipped").findValuesAsText("employeeId")).contains(noContract.toString());
        // the hourly casual worked nothing in this period
        assertThat(run.get("skipped").findValuesAsText("reason")).contains("No locked timesheet hours in this period");
    }

    @Test
    void rules_validateAndAreScopedCentrally() throws Exception {
        Map<String, Object> bad = new HashMap<>(Map.of("code", "TAX", "name", "Tax", "kind", "EMPLOYEE_DEDUCTION", "method", "BRACKETS", "base", "TAXABLE",
                "effectiveFrom", "2020-01-01", "brackets", List.of(Map.of("upTo", 500, "ratePercent", 5))));
        postJson("/hr/payroll-rules", bad, admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The last band must be open-ended"));
        Map<String, Object> preTaxOnTaxable = Map.of("code", "X", "name", "X", "kind", "EMPLOYEE_DEDUCTION", "method", "PERCENT", "base", "TAXABLE",
                "ratePercent", 5, "preTax", true, "effectiveFrom", "2020-01-01");
        postJson("/hr/payroll-rules", preTaxOnTaxable, admin).andExpect(status().isBadRequest());
        Map<String, Object> ok = Map.of("code", "WELFARE", "name", "Welfare", "kind", "EMPLOYEE_DEDUCTION", "method", "FIXED", "base", "GROSS",
                "fixedAmount", 10, "effectiveFrom", "2020-01-01");
        postJson("/hr/payroll-rules", ok, clerk).andExpect(status().isForbidden()); // settings permission needed
        postJson("/hr/payroll-rules", ok, admin).andExpect(status().isCreated());
        // ending the rule from a date removes it for runs after that date
        postJson("/hr/payroll-rules", Map.of("code", "WELFARE", "active", false, "effectiveFrom", monthStart.minusMonths(1).toString()), admin)
                .andExpect(status().isCreated());
        String id = createRun(clerk);
        JsonNode run = json(postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk));
        assertThat(payslipOf(run, chef).get("employeeDeductions").decimalValue()).isZero();
        assertThat(json(getJson("/hr/payroll-rules", clerk).andExpect(status().isOk()))).hasSize(2);
    }
}
