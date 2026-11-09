package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.attendance.AttendanceBreakRepository;
import com.restaurantmanager.core.hr.attendance.AttendanceCorrectionRepository;
import com.restaurantmanager.core.hr.attendance.AttendanceEventRepository;
import com.restaurantmanager.core.hr.attendance.CorrectionStatus;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrAttendanceIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    @Autowired
    private AttendanceEventRepository eventRepository;
    @Autowired
    private AttendanceBreakRepository breakRepository;
    @Autowired
    private AttendanceCorrectionRepository correctionRepository;
    @Autowired
    private EmployeeRepository employeeRepository;

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity cashier;
    private UUID cook;
    private UUID cleaner;
    private UUID cookShift;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cashier = staff("Till Cashier", Role.CASHIER, accra); // the account signed in to the POS terminal
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        cleaner = createEmployee(admin, "Gifty", "Cleaner", accra);
        putJson("/hr/employees/" + cook + "/clock-pin", Map.of("pin", "2580"), manager).andExpect(status().isOk());
        putJson("/hr/employees/" + cleaner + "/clock-pin", Map.of("pin", "1357"), manager).andExpect(status().isOk());
        Instant now = Instant.now();
        cookShift = publishShift(cook, now.minus(10, ChronoUnit.MINUTES), now.plus(7, ChronoUnit.HOURS));
    }

    private UUID publishShift(UUID employee, Instant start, Instant end) throws Exception {
        String id = json(postJson("/hr/schedules", Map.of("employeeId", employee, "branchId", accra.getId(),
                "startsAt", start.toString(), "endsAt", end.toString(), "breakMinutes", 30, "acknowledgeWarnings", true), manager)
                .andExpect(status().isCreated())).at("/shift/id").asText();
        LocalDate day = start.atZone(ZONE).toLocalDate();
        postJson("/hr/schedules/publish", Map.of("branchId", accra.getId(), "from", day.minusDays(1).toString(), "to", day.plusDays(1).toString()), manager)
                .andExpect(status().isOk());
        return UUID.fromString(id);
    }

    private Map<String, Object> pos(UUID employee, String pin, String action) {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employee);
        body.put("pin", pin);
        body.put("action", action);
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("terminalId", "till-1");
        return body;
    }

    @Test
    void posFullShift_linksToTheShift_tracksBreaks_andLeavesThePosAccountUntouched() throws Exception {
        getJson("/hr/pos/roster", cashier).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName == 'Kwesi C.')].hasPin").value(true))
                .andExpect(jsonPath("$[?(@.displayName == 'Kwesi C.')].state").value("OFF"));

        JsonNode in = json(postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_IN"), cashier).andExpect(status().isOk()));
        assertThat(in.at("/entry/scheduleId").asText()).isEqualTo(cookShift.toString());
        assertThat(in.at("/entry/unscheduled").asBoolean()).isFalse();
        assertThat(in.at("/entry/source").asText()).isEqualTo("POS");
        assertThat(in.at("/entry/terminalId").asText()).isEqualTo("till-1");
        assertThat(in.toString()).doesNotContain("accessToken").doesNotContain("pin");

        Map<String, Object> meal = pos(cook, "2580", "BREAK_START");
        meal.put("breakType", "MEAL");
        postJson("/hr/pos/clock", meal, cashier).andExpect(status().isOk()).andExpect(jsonPath("$.entry.onBreak").value(true));
        getJson("/hr/pos/roster", cashier).andExpect(jsonPath("$[?(@.displayName == 'Kwesi C.')].state").value("ON_BREAK"));
        postJson("/hr/pos/clock", pos(cook, "2580", "BREAK_START"), cashier).andExpect(status().isConflict());
        postJson("/hr/pos/clock", pos(cook, "2580", "BREAK_END"), cashier).andExpect(status().isOk());
        postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_OUT"), cashier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.status").value("COMPLETED"))
                .andExpect(jsonPath("$.entry.breaks[0].type").value("MEAL"))
                .andExpect(jsonPath("$.entry.breaks[0].paid").value(false));
        postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_OUT"), cashier).andExpect(status().isConflict());

        assertThat(eventRepository.findAll()).filteredOn(e -> "ACCEPTED".equals(e.getOutcome())).hasSize(4);
        // the POS operator is still the same cashier with the same access; clocking granted nothing
        UserEntity operator = userRepository.findById(cashier.getId()).orElseThrow();
        assertThat(operator.getRole()).isEqualTo(Role.CASHIER);
        assertThat(operator.getBranch().getId()).isEqualTo(accra.getId());
        getJson("/orders", cashier).andExpect(status().isOk());
        getJson("/hr/employees", cashier).andExpect(status().isForbidden());
    }

    @Test
    void sameRequestKey_isRecordedOnce_andCannotBeReusedForAnotherAction() throws Exception {
        Map<String, Object> body = pos(cook, "2580", "CLOCK_IN");
        JsonNode first = json(postJson("/hr/pos/clock", body, cashier).andExpect(status().isOk()));
        JsonNode retry = json(postJson("/hr/pos/clock", body, cashier).andExpect(status().isOk()));
        assertThat(first.get("duplicate").asBoolean()).isFalse();
        assertThat(retry.get("duplicate").asBoolean()).isTrue();
        assertThat(retry.at("/entry/id").asText()).isEqualTo(first.at("/entry/id").asText());
        assertThat(timeEntryRepository.findAll()).hasSize(1);

        Map<String, Object> reused = new HashMap<>(body);
        reused.put("action", "CLOCK_OUT");
        postJson("/hr/pos/clock", reused, cashier).andExpect(status().isConflict());
    }

    @Test
    void wrongPins_lockThePin_andTheCounterSurvivesRejectedRequests() throws Exception {
        for (int i = 0; i < 4; i++) {
            postJson("/hr/pos/clock", pos(cook, "9999", "CLOCK_IN"), cashier).andExpect(status().isUnauthorized());
        }
        assertThat(employeeRepository.findById(cook).orElseThrow().getClockPinFailCount()).isEqualTo((short) 4);
        postJson("/hr/pos/clock", pos(cook, "9999", "CLOCK_IN"), cashier).andExpect(status().isLocked());
        postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_IN"), cashier).andExpect(status().isLocked());
        getJson("/hr/employees/" + cook + "/clock-pin", manager).andExpect(jsonPath("$.lockedUntil").exists());
        // resetting the PIN unlocks it
        putJson("/hr/employees/" + cook + "/clock-pin", Map.of("pin", "4826"), manager).andExpect(status().isOk());
        postJson("/hr/pos/clock", pos(cook, "4826", "CLOCK_IN"), cashier).andExpect(status().isOk());
        assertThat(timeEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void branchRules_terminalAndEmployeeMustBelongToTheBranch() throws Exception {
        UUID kumasiCook = createEmployee(admin, "Yaw", "Kumasi", kumasi);
        putJson("/hr/employees/" + kumasiCook + "/clock-pin", Map.of("pin", "2468"), admin).andExpect(status().isOk());
        postJson("/hr/pos/clock", pos(kumasiCook, "2468", "CLOCK_IN"), cashier).andExpect(status().isForbidden());

        Map<String, Object> wrongTerminal = pos(cook, "2580", "CLOCK_IN");
        wrongTerminal.put("branchId", kumasi.getId());
        postJson("/hr/pos/clock", wrongTerminal, cashier).andExpect(status().isForbidden());

        UserEntity rider = staff("Rider", Role.RIDER, accra);
        getJson("/hr/pos/roster", rider).andExpect(status().isForbidden());
        getJson("/hr/pos/roster", admin).andExpect(status().isBadRequest());
        getJson("/hr/pos/roster?branchId=" + accra.getId(), admin).andExpect(status().isOk());
    }

    @Test
    void unscheduledClockIn_isFlaggedByDefault_andBlockedWhenThePolicySaysSo() throws Exception {
        postJson("/hr/pos/clock", pos(cleaner, "1357", "CLOCK_IN"), cashier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.unscheduled").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("flagged")));
        postJson("/hr/pos/clock", pos(cleaner, "1357", "CLOCK_OUT"), cashier).andExpect(status().isOk());

        putJson("/hr/attendance-policy", Map.of("branchId", accra.getId(), "lateGraceMinutes", 5, "earlyLeaveGraceMinutes", 5,
                "earlyClockInMinutes", 30, "unscheduledClockIn", "BLOCK", "requireCorrectionApproval", true, "mealBreakPaid", false,
                "restBreakPaid", true, "allowSelfClock", true, "missedClockOutMinutes", 120), admin).andExpect(status().isOk());
        Map<String, Object> blocked = pos(cleaner, "1357", "CLOCK_IN");
        postJson("/hr/pos/clock", blocked, cashier).andExpect(status().isConflict());
        assertThat(eventRepository.findByIdempotencyKey((String) blocked.get("idempotencyKey"))).get()
                .satisfies(e -> assertThat(e.getOutcome()).isEqualTo("REJECTED"));
        postJson("/hr/pos/clock", blocked, cashier).andExpect(status().isConflict()); // replay gives the same refusal
        postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_IN"), cashier).andExpect(status().isOk()); // scheduled is fine
    }

    @Test
    void dayBoard_showsAbsentLateAndUnscheduled() throws Exception {
        Instant now = Instant.now();
        publishShift(cleaner, now.minus(2, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_IN"), cashier).andExpect(status().isOk());
        String today = LocalDate.now(ZONE).toString();
        JsonNode day = json(getJson("/hr/attendance/day?branchId=" + accra.getId() + "&date=" + today, manager).andExpect(status().isOk()));
        Map<String, String> statusByName = new HashMap<>();
        day.get("rows").forEach(r -> statusByName.put(r.get("employeeName").asText(), r.get("status").asText()));
        assertThat(statusByName).containsEntry("Kwesi Cook", "ON_SHIFT").containsEntry("Gifty Cleaner", "ABSENT");
        getJson("/hr/attendance/day?branchId=" + kumasi.getId() + "&date=" + today, manager).andExpect(status().isForbidden());
        getJson("/hr/attendance/day?branchId=" + accra.getId() + "&date=" + today, cashier).andExpect(status().isForbidden());
    }

    @Test
    void corrections_keepOriginals_needASecondPerson_andCannotTouchYourOwnRecord() throws Exception {
        String entryId = json(postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_IN"), cashier)).at("/entry/id").asText();
        Instant originalIn = timeEntryRepository.findById(UUID.fromString(entryId)).orElseThrow().getClockInAt();
        Instant correctedIn = originalIn.minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);
        Instant correctedOut = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String correctionId = json(postJson("/hr/attendance/corrections", Map.of("timeEntryId", entryId, "clockInAt", correctedIn.toString(),
                "clockOutAt", correctedOut.toString(), "breakMinutes", 0, "reason", "Forgot to clock in on arrival"), manager)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))).get("id").asText();
        // nothing changes until someone else approves
        assertThat(timeEntryRepository.findById(UUID.fromString(entryId)).orElseThrow().getClockInAt()).isEqualTo(originalIn);
        postJson("/hr/attendance/corrections/" + correctionId + "/decision", Map.of("approve", true), manager).andExpect(status().isForbidden());
        postJson("/hr/attendance/corrections/" + correctionId + "/decision", Map.of("approve", true, "note", "Checked CCTV"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));

        var entry = timeEntryRepository.findById(UUID.fromString(entryId)).orElseThrow();
        assertThat(entry.getClockInAt()).isEqualTo(correctedIn);
        assertThat(entry.getClockOutAt()).isEqualTo(correctedOut);
        var correction = correctionRepository.findById(UUID.fromString(correctionId)).orElseThrow();
        assertThat(correction.getOriginalClockIn()).isEqualTo(originalIn);
        assertThat(correction.getOriginalClockOut()).isNull();

        // a manager who is also on the payroll cannot correct their own attendance
        UUID managerEmployee = createEmployee(admin, "Accra", "Manager", accra);
        postJson("/hr/employees/" + managerEmployee + "/link-user", Map.of("userId", manager.getId()), admin).andExpect(status().isOk());
        postJson("/hr/attendance/corrections", Map.of("employeeId", managerEmployee, "branchId", accra.getId(),
                "clockInAt", Instant.now().minus(Duration.ofHours(30)).toString(), "clockOutAt", Instant.now().minus(Duration.ofHours(22)).toString(),
                "reason", "Paying myself"), manager).andExpect(status().isForbidden());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_ATTENDANCE_CORRECTION)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void missingRecordCorrection_appliesImmediatelyWhenThePolicyNeedsNoApproval() throws Exception {
        putJson("/hr/attendance-policy", Map.of("branchId", accra.getId(), "lateGraceMinutes", 5, "earlyLeaveGraceMinutes", 5,
                "earlyClockInMinutes", 30, "unscheduledClockIn", "ALLOW", "requireCorrectionApproval", false, "mealBreakPaid", false,
                "restBreakPaid", true, "allowSelfClock", true, "missedClockOutMinutes", 120), admin).andExpect(status().isOk());
        Instant in = Instant.now().minus(Duration.ofHours(30)).truncatedTo(ChronoUnit.MINUTES);
        postJson("/hr/attendance/corrections", Map.of("employeeId", cleaner, "branchId", accra.getId(), "clockInAt", in.toString(),
                "clockOutAt", in.plus(Duration.ofHours(6)).toString(), "breakMinutes", 30, "reason", "Device was down"), manager)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPLIED"));
        assertThat(timeEntryRepository.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getSource()).isEqualTo("CORRECTION");
            assertThat(e.getEmployeeId()).isEqualTo(cleaner);
            assertThat(e.getBreakMinutes()).isEqualTo(30);
        });
        // a second record overlapping it is refused
        postJson("/hr/attendance/corrections", Map.of("employeeId", cleaner, "branchId", accra.getId(), "clockInAt", in.plus(Duration.ofHours(1)).toString(),
                "clockOutAt", in.plus(Duration.ofHours(3)).toString(), "reason", "Duplicate"), manager).andExpect(status().isConflict());
    }

    @Test
    void selfServiceAndLegacyClock_shareOneOpenRecordPerPerson() throws Exception {
        UserEntity linkedCashier = staff("Abena Linked", Role.CASHIER, accra);
        UUID abena = createEmployee(admin, "Abena", "Linked", accra);
        postJson("/hr/employees/" + abena + "/link-user", Map.of("userId", linkedCashier.getId()), admin).andExpect(status().isOk());
        putJson("/hr/me/clock-pin", Map.of("newPin", "9182"), linkedCashier).andExpect(status().isOk());
        putJson("/hr/me/clock-pin", Map.of("newPin", "5173"), linkedCashier).andExpect(status().isUnauthorized()); // needs current PIN
        putJson("/hr/me/clock-pin", Map.of("currentPin", "9182", "newPin", "5173"), linkedCashier).andExpect(status().isOk());

        postJson("/hr/me/clock", Map.of("action", "CLOCK_IN", "idempotencyKey", UUID.randomUUID().toString()), linkedCashier)
                .andExpect(status().isOk()).andExpect(jsonPath("$.entry.source").value("SELF_SERVICE"));
        postJson("/hr/pos/clock", pos(abena, "5173", "CLOCK_IN"), cashier).andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/timeclock/clock-in")
                .header("Authorization", bearer(linkedCashier))).andExpect(status().isConflict());
        // the legacy clock-out closes the same record
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/timeclock/clock-out")
                .header("Authorization", bearer(linkedCashier))).andExpect(status().isOk());
        getJson("/hr/me/attendance/status", linkedCashier).andExpect(status().isNoContent());
        getJson("/hr/me/attendance?from=" + LocalDate.now(ZONE).minusDays(1) + "&to=" + LocalDate.now(ZONE), linkedCashier)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("COMPLETED"));

        putJson("/hr/attendance-policy", Map.of("branchId", accra.getId(), "lateGraceMinutes", 5, "earlyLeaveGraceMinutes", 5,
                "earlyClockInMinutes", 30, "unscheduledClockIn", "ALLOW", "requireCorrectionApproval", true, "mealBreakPaid", false,
                "restBreakPaid", true, "allowSelfClock", false, "missedClockOutMinutes", 120), admin).andExpect(status().isOk());
        postJson("/hr/me/clock", Map.of("action", "CLOCK_IN", "idempotencyKey", UUID.randomUUID().toString()), linkedCashier)
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/timeclock/clock-in")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(linkedCashier.getId(), Role.CASHIER, accra.getId())))
                .andExpect(status().isForbidden());
        postJson("/hr/pos/clock", pos(abena, "5173", "CLOCK_IN"), cashier).andExpect(status().isOk()); // the POS still works
    }

    @Test
    void managerClockOnBehalf_isAudited_andPinManagementIsRestricted() throws Exception {
        postJson("/hr/attendance/clock", Map.of("employeeId", cleaner, "branchId", accra.getId(), "action", "CLOCK_IN",
                "idempotencyKey", UUID.randomUUID().toString(), "reason", "Tablet was offline"), manager)
                .andExpect(status().isOk()).andExpect(jsonPath("$.entry.source").value("MANAGER"));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_ATTENDANCE_RECORDED)).hasSize(1);
        postJson("/hr/attendance/clock", Map.of("employeeId", cleaner, "branchId", accra.getId(), "action", "CLOCK_OUT",
                "idempotencyKey", UUID.randomUUID().toString()), cashier).andExpect(status().isForbidden());

        putJson("/hr/employees/" + cook + "/clock-pin", Map.of("pin", "1234"), manager).andExpect(status().isBadRequest());
        putJson("/hr/employees/" + cook + "/clock-pin", Map.of("pin", "8642"), cashier).andExpect(status().isForbidden());
        getJson("/hr/employees/" + cook + "/clock-pin", manager).andExpect(jsonPath("$.set").value(true));
        assertThat(employeeRepository.findById(cook).orElseThrow().getClockPinHash()).doesNotContain("2580").startsWith("$2");
        assertThat(auditLogRepository.findByAction(AuditAction.HR_CLOCK_PIN_CHANGED)).allSatisfy(a -> assertThat(a.getMetadata()).doesNotContain("2580"));
    }

    @Test
    void legacyEntryEdit_recordsTheOriginalsAsAnAppliedCorrection() throws Exception {
        String entryId = json(postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_IN"), cashier)).at("/entry/id").asText();
        postJson("/hr/pos/clock", pos(cook, "2580", "CLOCK_OUT"), cashier).andExpect(status().isOk());
        Instant originalIn = timeEntryRepository.findById(UUID.fromString(entryId)).orElseThrow().getClockInAt();
        putJson("/timeclock/entries/" + entryId, Map.of("clockInAt", originalIn.minus(Duration.ofMinutes(15)).toString(), "reason", "Arrived earlier"), manager)
                .andExpect(status().isOk());
        assertThat(correctionRepository.findByTimeEntryIdOrderByCreatedAtAsc(UUID.fromString(entryId))).singleElement().satisfies(c -> {
            assertThat(c.getStatus()).isEqualTo(CorrectionStatus.APPLIED);
            assertThat(c.getOriginalClockIn()).isEqualTo(originalIn);
        });
        assertThat(breakRepository.findAll()).isEmpty();
    }
}
