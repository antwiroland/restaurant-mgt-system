package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrTimesheetIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UUID cook;
    private LocalDate monday;
    private LocalDate sunday;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        monday = LocalDate.now(ZONE).minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        sunday = monday.plusDays(6);
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private UUID entry(UUID employee, LocalDate day, int from, int to, int unpaidBreak) {
        TimeEntryEntity e = new TimeEntryEntity();
        e.setEmployeeId(employee);
        e.setBranchId(accra.getId());
        e.setClockInAt(at(day, from));
        e.setClockOutAt(at(day, to));
        e.setBreakMinutes(unpaidBreak);
        e.setSource("POS");
        return timeEntryRepository.save(e).getId();
    }

    private void shift(UUID employee, LocalDate day, int from, int to) {
        WorkScheduleEntity s = new WorkScheduleEntity();
        s.setEmployeeId(employee);
        s.setBranchId(accra.getId());
        s.setStartsAt(at(day, from));
        s.setEndsAt(at(day, to));
        s.setBreakMinutes(30);
        s.setStatus(ScheduleStatus.PUBLISHED);
        workScheduleRepository.save(s);
    }

    private Map<String, Object> period() {
        return Map.of("branchId", accra.getId(), "from", monday.toString(), "to", sunday.toString());
    }

    private String generateAndGetId() throws Exception {
        postJson("/hr/timesheets/generate", period(), manager).andExpect(status().isOk());
        JsonNode list = json(getJson("/hr/timesheets?branchId=" + accra.getId() + "&from=" + monday + "&to=" + sunday, manager));
        return list.get(0).get("id").asText();
    }

    @Test
    void generate_snapshotsAttendanceWithAbsencesAndDailyOvertime() throws Exception {
        entry(cook, monday, 8, 19, 60);          // 600 paid -> 120 OT over 480
        entry(cook, monday.plusDays(1), 9, 17, 30); // 450 paid
        shift(cook, monday.plusDays(2), 9, 17);   // no attendance -> absent
        JsonNode result = json(postJson("/hr/timesheets/generate", period(), manager).andExpect(status().isOk()));
        assertThat(result.get("created").asInt()).isEqualTo(1);

        String id = json(getJson("/hr/timesheets?branchId=" + accra.getId() + "&from=" + monday + "&to=" + sunday, manager)).get(0).get("id").asText();
        JsonNode detail = json(getJson("/hr/timesheets/" + id, manager).andExpect(status().isOk()));
        assertThat(detail.at("/summary/status").asText()).isEqualTo("DRAFT");
        assertThat(detail.at("/summary/paidMinutes").asInt()).isEqualTo(1050);
        assertThat(detail.at("/summary/overtimeComputedMinutes").asInt()).isEqualTo(120);
        assertThat(detail.at("/summary/payableOvertimeMinutes").asInt()).isZero();
        assertThat(detail.at("/summary/absenceCount").asInt()).isEqualTo(1);
        assertThat(detail.get("lines").size()).isEqualTo(3);
        assertThat(detail.at("/lines/2/flags/0").asText()).isEqualTo("ABSENT");
        assertThat(detail.get("ruleSnapshot").asText()).contains("CONFIGURATION");

        // refreshing a draft picks up new attendance
        entry(cook, monday.plusDays(3), 9, 13, 0);
        postJson("/hr/timesheets/" + id + "/refresh", Map.of(), manager).andExpect(jsonPath("$.summary.paidMinutes").value(1290));
    }

    @Test
    void workflow_reviewApproveLock_withOvertimeApprovalAndPayrollLock() throws Exception {
        entry(cook, monday, 8, 19, 60);
        String id = generateAndGetId();
        UserEntity cashier = staff("Cashier", Role.CASHIER, accra);

        postJson("/hr/timesheets/" + id + "/approve", Map.of(), manager).andExpect(status().isConflict()); // not reviewed yet
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), cashier).andExpect(status().isNotFound());
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), manager).andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("SUBMITTED"));
        postJson("/hr/timesheets/" + id + "/review", Map.of("note", "Checked against rota"), manager).andExpect(jsonPath("$.summary.status").value("REVIEWED"));
        postJson("/hr/timesheets/" + id + "/approve", Map.of("overtimeApprovedMinutes", 500), manager).andExpect(status().isBadRequest());
        postJson("/hr/timesheets/" + id + "/approve", Map.of("overtimeApprovedMinutes", 60, "note", "Only the stock-take hour"), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("APPROVED"))
                .andExpect(jsonPath("$.summary.payableOvertimeMinutes").value(60));

        postJson("/hr/timesheets/" + id + "/lock", Map.of(), manager).andExpect(status().isForbidden()); // payroll permission
        postJson("/hr/timesheets/" + id + "/lock", Map.of(), admin).andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("LOCKED"));
        postJson("/hr/timesheets/" + id + "/reopen", Map.of("reason", "Mistake"), admin).andExpect(status().isConflict());
        postJson("/hr/timesheets/generate", period(), manager).andExpect(jsonPath("$.skipped[0].reason").value("Already locked"));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_TIMESHEET_CHANGED))
                .anyMatch(a -> a.getMetadata().contains("\"overtimeApprovedMinutes\":60"));
    }

    @Test
    void submittedPeriod_isFrozenForCorrectionsUntilReopened() throws Exception {
        UUID entryId = entry(cook, monday, 9, 17, 30);
        String id = generateAndGetId();
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), manager).andExpect(status().isOk());

        Map<String, Object> correction = Map.of("timeEntryId", entryId, "clockInAt", at(monday, 8).toString(),
                "clockOutAt", at(monday, 17).toString(), "breakMinutes", 30, "reason", "Opened early");
        postJson("/hr/attendance/corrections", correction, manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Reopen")));
        putJson("/timeclock/entries/" + entryId, Map.of("clockInAt", at(monday, 8).toString(), "reason", "x"), manager).andExpect(status().isConflict());

        postJson("/hr/timesheets/" + id + "/reopen", Map.of("reason", "Correction needed"), manager).andExpect(jsonPath("$.summary.status").value("DRAFT"));
        postJson("/hr/attendance/corrections", correction, manager).andExpect(status().isCreated());
    }

    @Test
    void submitIsBlockedByOpenShiftsAndPendingCorrections() throws Exception {
        TimeEntryEntity open = new TimeEntryEntity();
        open.setEmployeeId(cook);
        open.setBranchId(accra.getId());
        open.setClockInAt(at(monday, 9));
        open.setSource("POS");
        UUID openId = timeEntryRepository.save(open).getId();
        String id = generateAndGetId();
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no clock-out")));

        postJson("/hr/attendance/corrections", Map.of("timeEntryId", openId, "clockInAt", at(monday, 9).toString(),
                "clockOutAt", at(monday, 17).toString(), "reason", "Forgot to clock out"), manager).andExpect(status().isCreated());
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("waiting for approval")));
    }

    @Test
    void ownTimesheet_selfSubmitButNeverSelfApprove() throws Exception {
        postJson("/hr/employees/" + cook + "/link-user", Map.of("userId", manager.getId()), admin).andExpect(status().isOk());
        entry(cook, monday, 9, 17, 30);
        String id = generateAndGetId();
        getJson("/hr/me/timesheets", manager).andExpect(jsonPath("$[0].id").value(id));
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), manager).andExpect(status().isOk());
        postJson("/hr/timesheets/" + id + "/review", Map.of(), manager).andExpect(status().isForbidden());
        postJson("/hr/timesheets/" + id + "/review", Map.of(), admin).andExpect(status().isOk());
        postJson("/hr/timesheets/" + id + "/approve", Map.of(), manager).andExpect(status().isForbidden());
        postJson("/hr/timesheets/" + id + "/approve", Map.of(), admin).andExpect(jsonPath("$.summary.status").value("APPROVED"));
    }

    @Test
    void effectiveDatedRule_andHolidays_changeTheFigures() throws Exception {
        entry(cook, monday, 8, 18, 0);              // 600
        entry(cook, monday.plusDays(3), 8, 18, 0);  // 600
        postJson("/hr/overtime-rules", Map.of("branchId", accra.getId(), "effectiveFrom", monday.plusDays(2).toString(),
                "dailyThresholdMinutes", 540, "dailyMultiplier", "1.50", "weeklyMultiplier", "1.50", "requiresApproval", true), manager)
                .andExpect(status().isForbidden()); // settings permission
        postJson("/hr/overtime-rules", Map.of("branchId", accra.getId(), "effectiveFrom", monday.plusDays(2).toString(),
                "dailyThresholdMinutes", 540, "dailyMultiplier", "1.50", "weeklyMultiplier", "1.50", "requiresApproval", true), admin)
                .andExpect(status().isCreated());
        postJson("/hr/holidays", Map.of("date", monday.toString(), "name", "Founders' Day"), admin).andExpect(status().isCreated());
        postJson("/hr/holidays", Map.of("date", monday.toString(), "name", "Duplicate"), admin).andExpect(status().isConflict());

        String id = generateAndGetId();
        JsonNode detail = json(getJson("/hr/timesheets/" + id, manager));
        // Monday: default 480 threshold -> 120; Thursday: branch rule 540 -> 60
        assertThat(detail.at("/summary/overtimeComputedMinutes").asInt()).isEqualTo(180);
        assertThat(detail.at("/summary/holidayMinutes").asInt()).isEqualTo(600);
        assertThat(detail.get("ruleSnapshot").asText()).contains("BRANCH").contains("CONFIGURATION");
        getJson("/hr/overtime-rules?branchId=" + accra.getId(), manager).andExpect(jsonPath("$[0].note").value(org.hamcrest.Matchers.startsWith("In force today")));
    }

    @Test
    void preAuthorisedOvertime_prefillsApproval_andRequesterCannotApproveIt() throws Exception {
        entry(cook, monday, 8, 19, 60); // 120 OT
        String requestId = json(postJson("/hr/overtime-requests", Map.of("employeeId", cook, "workDate", monday.toString(),
                "minutes", 90, "reason", "Inventory count"), manager).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/overtime-requests/" + requestId + "/decision", Map.of("approve", true), manager).andExpect(status().isForbidden());
        postJson("/hr/overtime-requests/" + requestId + "/decision", Map.of("approve", true), admin).andExpect(jsonPath("$.status").value("APPROVED"));

        String id = generateAndGetId();
        getJson("/hr/timesheets/" + id, manager).andExpect(jsonPath("$.preApprovedOvertimeMinutes").value(90));
        postJson("/hr/timesheets/" + id + "/submit", Map.of(), manager).andExpect(status().isOk());
        postJson("/hr/timesheets/" + id + "/review", Map.of(), manager).andExpect(status().isOk());
        postJson("/hr/timesheets/" + id + "/approve", Map.of(), manager).andExpect(jsonPath("$.summary.overtimeApprovedMinutes").value(90));
    }

    @Test
    void branchScope_andGenerationSkipsOverlappingPeriods() throws Exception {
        entry(cook, monday, 9, 17, 30);
        String id = generateAndGetId();
        UserEntity kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        getJson("/hr/timesheets?branchId=" + accra.getId() + "&from=" + monday + "&to=" + sunday, kumasiManager).andExpect(status().isForbidden());
        getJson("/hr/timesheets/" + id, kumasiManager).andExpect(status().isNotFound());
        postJson("/hr/timesheets/generate", Map.of("branchId", accra.getId(), "from", monday.minusDays(1).toString(), "to", sunday.minusDays(1).toString()), manager)
                .andExpect(jsonPath("$.skipped[0].reason").value(org.hamcrest.Matchers.startsWith("Overlaps")));
        postJson("/hr/timesheets/generate", Map.of("branchId", accra.getId(), "from", monday.toString(), "to", monday.plusDays(40).toString()), manager)
                .andExpect(status().isBadRequest());
        Map<String, Object> body = new HashMap<>(period());
        body.put("branchId", kumasi.getId());
        postJson("/hr/timesheets/generate", body, manager).andExpect(status().isForbidden());
    }
}
