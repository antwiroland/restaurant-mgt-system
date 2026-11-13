package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.leave.LeaveTypeEntity;
import com.restaurantmanager.core.hr.leave.LeaveTypeRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineEntity;
import com.restaurantmanager.core.hr.timesheet.TimesheetLineRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetRepository;
import com.restaurantmanager.core.hr.timesheet.TimesheetStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ghana preset and returns, ID-number checks, year to date, final pay, loans and the run's cost by branch. */
class HrPayrollStatutoryIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    @Autowired
    private TimesheetRepository timesheetRepository;
    @Autowired
    private TimesheetLineRepository timesheetLineRepository;
    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    private BranchEntity accra;
    private BranchEntity tema;
    private UserEntity admin;
    private UserEntity clerk;
    private UUID chef;
    private LocalDate monthStart;
    private LocalDate monthEnd;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("PSA");
        tema = branch("PST");
        admin = staff("Owner", Role.ADMIN, null);
        clerk = staff("Payroll Clerk", Role.CASHIER, accra);
        grant(clerk, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.EMPLOYEE_VIEW);
        chef = createEmployee(admin, "Kwesi", "Chef", accra);
        contract(chef, "MONTHLY", "3000.00");
        monthStart = LocalDate.now(ZONE).minusMonths(1).withDayOfMonth(1);
        monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
    }

    private void contract(UUID employee, String salaryType, String amount) throws Exception {
        String id = json(postJson("/hr/employees/" + employee + "/contracts", Map.of("contractType", "PERMANENT", "startDate", "2025-01-15",
                "salaryType", salaryType, "salaryAmount", amount, "weeklyHours", 40), admin).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + id + "/activate", Map.of(), admin).andExpect(status().isOk());
    }

    private String calculatedRun() throws Exception {
        String id = json(postJson("/hr/payroll-runs", Map.of("branchId", accra.getId(), "periodStart", monthStart.toString(),
                "periodEnd", monthEnd.toString()), clerk).andExpect(status().isCreated())).at("/summary/id").asText();
        postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk).andExpect(status().isOk());
        return id;
    }

    private JsonNode slip(String runId, UUID employee) throws Exception {
        for (JsonNode p : json(getJson("/hr/payroll-runs/" + runId, clerk)).get("payslips")) {
            if (p.get("employeeId").asText().equals(employee.toString())) return p;
        }
        throw new AssertionError("No payslip");
    }

    private String csv(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(clerk))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void withoutTaxRules_payslipsWarn() throws Exception {
        String run = calculatedRun();
        assertThat(slip(run, chef).get("issues").toString()).contains("NO_PAYE_RULE").contains("NO_SSNIT_RULE");
    }

    @Test
    void ghanaPreset_returns_idChecks_andYearToDate() throws Exception {
        JsonNode added = json(postJson("/hr/payroll-rules/presets/ghana", Map.of("effectiveFrom", "2020-01-01"), admin).andExpect(status().isOk()));
        assertThat(added).hasSize(3);
        assertThat(json(postJson("/hr/payroll-rules/presets/ghana", Map.of("effectiveFrom", "2020-01-01"), admin))).isEmpty();

        String run = calculatedRun();
        JsonNode first = slip(run, chef);
        assertThat(first.get("issues").toString()).contains("NO_SSNIT_NUMBER").contains("NO_TIN").doesNotContain("NO_PAYE_RULE");
        assertThat(first.get("employeeDeductions").decimalValue()).isGreaterThan(new java.math.BigDecimal("165.00"));

        Map<String, Object> body = employeeBody("Kwesi", "Chef", accra);
        body.put("ssnitNumber", "C123456789012");
        body.put("nationalId", "GHA-123456789-0");
        body.put("version", json(getJson("/hr/employees/" + chef, admin)).get("version").asLong());
        putJson("/hr/employees/" + chef, body, admin).andExpect(status().isOk());
        postJson("/hr/payroll-runs/" + run + "/calculate", Map.of(), clerk).andExpect(status().isOk());
        JsonNode slip = slip(run, chef);
        assertThat(slip.get("issues").toString()).doesNotContain("NO_SSNIT_NUMBER").doesNotContain("NO_TIN");

        String ssnit = csv("/hr/payroll-runs/" + run + "/returns/ssnit");
        assertThat(ssnit).contains("C123456789012").contains("165.00").contains("390.00");
        String paye = csv("/hr/payroll-runs/" + run + "/returns/paye");
        assertThat(paye).contains("GHA-123456789-0").contains("2835.00");

        JsonNode detail = json(getJson("/hr/payslips/" + slip.get("id").asText(), clerk));
        assertThat(detail.get("ssnitNumber").asText()).isEqualTo("C123456789012");
        assertThat(detail.at("/yearToDate/gross").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(detail.at("/yearToDate/ssnitEmployee").decimalValue()).isEqualByComparingTo("165.00");
    }

    @Test
    void loan_isRepaidByInstalments_recalculationAndCancelDoNotDoubleCount() throws Exception {
        String loan = json(postJson("/hr/employees/" + chef + "/recurring-deductions", Map.of("kind", "LOAN", "description", "Staff loan",
                "installmentAmount", 200, "totalAmount", 500, "startDate", monthStart.toString()), clerk).andExpect(status().isCreated())).get("id").asText();
        String run = calculatedRun();
        assertThat(slip(run, chef).get("employeeDeductions").decimalValue()).isEqualByComparingTo("200.00");
        postJson("/hr/payroll-runs/" + run + "/calculate", Map.of(), clerk).andExpect(status().isOk());
        JsonNode list = json(getJson("/hr/employees/" + chef + "/recurring-deductions", clerk));
        assertThat(list.get(0).get("repaid").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(list.get(0).get("remaining").decimalValue()).isEqualByComparingTo("300.00");

        postJson("/hr/payroll-runs/" + run + "/cancel", Map.of("reason", "Redo"), clerk).andExpect(status().isOk());
        assertThat(json(getJson("/hr/employees/" + chef + "/recurring-deductions", clerk)).get(0).get("repaid").decimalValue()).isEqualByComparingTo("0");

        postJson("/hr/recurring-deductions/" + loan + "/stop", Map.of("reason", "Written off"), clerk).andExpect(status().isOk());
        String again = calculatedRun();
        assertThat(slip(again, chef).get("employeeDeductions").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void finalPay_paysUnusedAnnualLeave_andRecoversTheWholeLoan() throws Exception {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("code", "ANNUALX");
        type.put("name", "Annual leave");
        type.put("paid", true);
        type.put("deductsBalance", true);
        type.put("requiresDocument", false);
        type.put("allowNegative", false);
        type.put("countBasis", "WORKDAYS");
        type.put("approvalSteps", List.of("HR"));
        String typeId = json(postJson("/hr/leave-types", type, admin).andExpect(status().isCreated())).get("id").asText();
        LeaveTypeEntity entity = leaveTypeRepository.findById(UUID.fromString(typeId)).orElseThrow();
        entity.setPayOutOnExit(true);
        leaveTypeRepository.save(entity);
        postJson("/hr/leave-policies", Map.of("leaveTypeId", typeId, "annualDays", 20, "accrual", "UPFRONT", "carryOverMaxDays", 0,
                "effectiveFrom", "2020-01-01"), admin).andExpect(status().isCreated());
        postJson("/hr/employees/" + chef + "/leave-balances/adjust", Map.of("leaveTypeId", typeId, "year", monthStart.getYear(), "days", -15,
                "note", "Taken earlier"), admin).andExpect(status().isOk());
        postJson("/hr/employees/" + chef + "/recurring-deductions", Map.of("kind", "ADVANCE", "description", "Salary advance",
                "installmentAmount", 100, "totalAmount", 400, "startDate", monthStart.toString()), clerk).andExpect(status().isCreated());

        LocalDate lastDay = monthStart.plusDays(9);
        postJson("/hr/employees/" + chef + "/status", Map.of("status", "RESIGNED", "effectiveDate", lastDay.toString(), "reason", "Relocating"), admin)
                .andExpect(status().isOk());

        String run = calculatedRun();
        JsonNode slip = slip(run, chef);
        assertThat(slip.get("issues").toString()).contains("FINAL_PAY");
        JsonNode lines = json(getJson("/hr/payslips/" + slip.get("id").asText(), clerk)).get("lines");
        JsonNode payout = null;
        JsonNode advance = null;
        for (JsonNode l : lines) {
            if (l.get("code").asText().equals("LEAVE_PAYOUT")) payout = l;
            if (l.get("code").asText().equals("RECURRING")) advance = l;
        }
        assertThat(payout).isNotNull();
        assertThat(payout.get("quantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(advance).isNotNull();
        assertThat(advance.get("amount").decimalValue()).isEqualByComparingTo("400.00");
    }

    @Test
    void costAllocation_splitsByMinutesWorkedAtEachBranch() throws Exception {
        UUID casual = createEmployee(admin, "Afia", "Casual", accra);
        contract(casual, "HOURLY", "20.00");
        TimesheetEntity t = new TimesheetEntity();
        t.setEmployeeId(casual);
        t.setBranchId(accra.getId());
        t.setPeriodStart(monthStart);
        t.setPeriodEnd(monthStart.plusDays(6));
        t.setStatus(TimesheetStatus.LOCKED);
        t.setPaidMinutes(600);
        t.setRegularMinutes(600);
        t.setGeneratedAt(Instant.now());
        UUID sheet = timesheetRepository.save(t).getId();
        for (Object[] part : new Object[][]{{accra, 0, 450}, {tema, 1, 150}}) {
            TimesheetLineEntity line = new TimesheetLineEntity();
            line.setTimesheetId(sheet);
            line.setWorkDate(monthStart.plusDays((int) part[1]));
            line.setBranchId(((BranchEntity) part[0]).getId());
            line.setPaidMinutes((int) part[2]);
            timesheetLineRepository.save(line);
        }
        String run = calculatedRun();
        JsonNode allocation = json(getJson("/hr/payroll-runs/" + run + "/cost-allocation", clerk).andExpect(status().isOk()));
        java.math.BigDecimal temaGross = null;
        for (JsonNode b : allocation.get("branches")) {
            if (b.get("branchId").asText().equals(tema.getId().toString())) temaGross = b.get("gross").decimalValue();
        }
        assertThat(temaGross).isEqualByComparingTo("50.00"); // a quarter of 200.00
        assertThat(allocation.get("splitEmployees")).hasSize(1);
    }
}
