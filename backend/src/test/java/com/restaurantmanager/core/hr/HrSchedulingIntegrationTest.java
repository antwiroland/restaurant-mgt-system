package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrSchedulingIntegrationTest extends HrIntegrationTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    private com.restaurantmanager.core.workforce.WorkScheduleRepository workScheduleRepository;

    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity cashier;
    private UUID cashierEmployee;
    private UUID cook;
    private LocalDate monday;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cashier = staff("Efua Cashier", Role.CASHIER, accra);
        cashierEmployee = createEmployee(admin, "Efua", "Cashier", accra);
        postJson("/hr/employees/" + cashierEmployee + "/link-user", Map.of("userId", cashier.getId()), admin).andExpect(status().isOk());
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        monday = LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private Map<String, Object> shift(UUID employee, LocalDate day, int from, int to) {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employee);
        body.put("branchId", accra.getId());
        body.put("startsAt", at(day, from).toString());
        body.put("endsAt", (to <= from ? at(day.plusDays(1), to) : at(day, to)).toString());
        body.put("breakMinutes", 30);
        return body;
    }

    private String createShift(UserEntity actor, Map<String, Object> body) throws Exception {
        return json(postJson("/hr/schedules", body, actor).andExpect(status().isCreated())).at("/shift/id").asText();
    }

    private void publishWeek(UserEntity actor) throws Exception {
        postJson("/hr/schedules/publish", Map.of("branchId", accra.getId(), "from", monday.toString(), "to", monday.plusDays(6).toString()), actor)
                .andExpect(status().isOk());
    }

    @Test
    void draftsAreInvisibleToStaffUntilPublished_includingTheLegacyApis() throws Exception {
        createShift(manager, shift(cashierEmployee, monday, 9, 17));
        createShift(manager, shift(cook, monday, 9, 17)); // no login: schedulable anyway

        String range = "?from=" + monday + "&to=" + monday.plusDays(6);
        getJson("/hr/me/shifts" + range, cashier).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        getJson("/scheduling/my-shifts?from=" + at(monday, 0) + "&to=" + at(monday.plusDays(7), 0), cashier).andExpect(jsonPath("$.length()").value(0));
        getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(6), manager)
                .andExpect(jsonPath("$.shifts.length()").value(2))
                .andExpect(jsonPath("$.shifts[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.employees.length()").value(2));

        postJson("/hr/schedules/publish", Map.of("branchId", accra.getId(), "from", monday.toString(), "to", monday.plusDays(6).toString()), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(2))
                .andExpect(jsonPath("$.employeesNotified").value(1));

        getJson("/hr/me/shifts" + range, cashier).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].paidMinutes").value(450));
        getJson("/scheduling/my-shifts?from=" + at(monday, 0) + "&to=" + at(monday.plusDays(7), 0), cashier)
                .andExpect(jsonPath("$.length()").value(1));
        // the legacy timesheet ignores shifts of employees without a login instead of failing
        getJson("/timeclock/timesheet?from=" + monday + "&to=" + monday.plusDays(6), manager).andExpect(status().isOk());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_SCHEDULE_PUBLISHED)).hasSize(1);
    }

    @Test
    void blockingIssuesReject_warningsNeedAcknowledgement() throws Exception {
        UUID kumasiCook = createEmployee(admin, "Yaw", "Kumasi", kumasi);
        postJson("/hr/schedules", shift(kumasiCook, monday, 9, 17), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blocking").value(true))
                .andExpect(jsonPath("$.issues[0].code").value("NOT_AT_BRANCH"));

        createShift(manager, shift(cook, monday, 14, 22));
        postJson("/hr/schedules", shift(cook, monday, 18, 23), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.issues[0].code").value("OVERLAP"));

        Map<String, Object> early = shift(cook, monday.plusDays(1), 5, 13);
        postJson("/hr/schedules", early, manager)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.blocking").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("SHORT_REST"));
        early.put("acknowledgeWarnings", true);
        postJson("/hr/schedules", early, manager)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acknowledgedWarnings[0].code").value("SHORT_REST"));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_SCHEDULE_CHANGED))
                .anyMatch(a -> a.getMetadata().contains("acknowledgedWarnings"));
    }

    @Test
    void templatesDriveTimesAndCoverage_openShiftsCountAsOpen() throws Exception {
        String evening = json(postJson("/hr/shift-templates", Map.of("name", "Evening service", "shiftType", "EVENING", "branchId", accra.getId(),
                "startTime", "16:00", "endTime", "23:30", "breakMinutes", 30, "days", List.of("MON", "TUE"), "minStaff", 2, "maxStaff", 3), manager)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days.length()").value(2))).get("id").asText();
        postJson("/hr/shift-templates", Map.of("name", "Everywhere", "shiftType", "MORNING", "startTime", "08:00", "endTime", "12:00",
                "breakMinutes", 0, "days", List.of("MON"), "minStaff", 1), manager).andExpect(status().isForbidden());

        JsonNode saved = json(postJson("/hr/schedules", Map.of("employeeId", cook, "branchId", accra.getId(), "templateId", evening,
                "date", monday.toString()), manager).andExpect(status().isCreated()));
        assertThat(Instant.parse(saved.at("/shift/startsAt").asText())).isEqualTo(at(monday, 16));
        assertThat(saved.at("/shift/breakMinutes").asInt()).isEqualTo(30);
        Map<String, Object> open = new HashMap<>(Map.of("branchId", accra.getId(), "templateId", evening, "date", monday.toString()));
        postJson("/hr/schedules", open, manager).andExpect(status().isCreated()).andExpect(jsonPath("$.shift.employeeId").doesNotExist());

        JsonNode board = json(getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(1), manager));
        JsonNode mondayCell = null;
        JsonNode tuesdayCell = null;
        for (JsonNode cell : board.get("coverage")) {
            if (cell.get("date").asText().equals(monday.toString())) mondayCell = cell;
            if (cell.get("date").asText().equals(monday.plusDays(1).toString())) tuesdayCell = cell;
        }
        assertThat(mondayCell.get("assigned").asLong()).isEqualTo(1);
        assertThat(mondayCell.get("open").asLong()).isEqualTo(1);
        assertThat(mondayCell.get("state").asText()).isEqualTo("UNDERSTAFFED");
        assertThat(tuesdayCell.get("assigned").asLong()).isZero();

        postJson("/hr/schedules", Map.of("employeeId", cashierEmployee, "branchId", accra.getId(), "templateId", evening,
                "date", monday.toString()), manager).andExpect(status().isCreated());
        board = json(getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday, manager));
        assertThat(board.at("/coverage/0/state").asText()).isEqualTo("OK");
    }

    @Test
    void branchScopeAndPermissions_areEnforced() throws Exception {
        UserEntity kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        String id = createShift(manager, shift(cook, monday, 9, 17));
        getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday, kumasiManager).andExpect(status().isForbidden());
        postJson("/hr/schedules/" + id + "/cancel", Map.of("reason", "x"), kumasiManager).andExpect(status().isNotFound());
        postJson("/hr/schedules", shift(cook, monday.plusDays(2), 9, 17), cashier).andExpect(status().isForbidden());
        postJson("/hr/schedules/publish", Map.of("branchId", accra.getId(), "from", monday.toString(), "to", monday.toString()), cashier)
                .andExpect(status().isForbidden());
        getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(90), manager).andExpect(status().isBadRequest());
    }

    @Test
    void cancelAndDeleteRules() throws Exception {
        String draft = createShift(manager, shift(cook, monday, 9, 17));
        String published = createShift(manager, shift(cashierEmployee, monday, 9, 17));
        publishWeek(manager);
        // the cook's draft is now published too; create a fresh draft
        String newDraft = createShift(manager, shift(cook, monday.plusDays(2), 9, 17));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/hr/schedules/" + published)
                .header("Authorization", bearer(manager))).andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/hr/schedules/" + newDraft)
                .header("Authorization", bearer(manager))).andExpect(status().isNoContent());

        postJson("/hr/schedules/" + published + "/cancel", Map.of(), manager).andExpect(status().isBadRequest());
        postJson("/hr/schedules/" + published + "/cancel", Map.of("reason", "Quiet night"), manager)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        putJson("/hr/schedules/" + published, shift(cashierEmployee, monday, 10, 17), manager).andExpect(status().isConflict());
        getJson("/hr/me/shifts?from=" + monday + "&to=" + monday, cashier).andExpect(jsonPath("$.length()").value(0));
        assertThat(workScheduleRepository.findById(UUID.fromString(draft)).orElseThrow().getStatus()).isEqualTo(ScheduleStatus.PUBLISHED);
    }

    @Test
    void publishIsAllOrNothing_whenADraftBecameInvalid() throws Exception {
        createShift(manager, shift(cashierEmployee, monday, 9, 17));
        createShift(manager, shift(cook, monday, 9, 17));
        // A suspension keeps the shift on the rota but makes it invalid (a resignation instead turns it into an open shift).
        postJson("/hr/employees/" + cook + "/status", Map.of("status", "SUSPENDED", "reason", "Investigation"), admin).andExpect(status().isOk());
        postJson("/hr/schedules/publish", Map.of("branchId", accra.getId(), "from", monday.toString(), "to", monday.plusDays(6).toString()), manager)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.containsString("Kwesi Cook")));
        assertThat(workScheduleRepository.findAll()).allMatch(s -> s.getStatus() == ScheduleStatus.DRAFT);
    }

    @Test
    void selfServiceAvailability_feedsSchedulingWarnings() throws Exception {
        postJson("/hr/me/availability", Map.of("kind", "UNAVAILABLE", "dayOfWeek", "MON", "note", "Evening classes"), cashier)
                .andExpect(status().isCreated());
        postJson("/hr/me/availability", Map.of("kind", "UNAVAILABLE", "dayOfWeek", "MON", "specificDate", monday.toString()), cashier)
                .andExpect(status().isBadRequest());
        getJson("/hr/employees/" + cashierEmployee + "/availability", manager).andExpect(jsonPath("$.length()").value(1));
        postJson("/hr/schedules", shift(cashierEmployee, monday, 9, 17), manager)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues[0].code").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.containsString("Evening classes")));
        getJson("/hr/me/availability", staff("Unlinked", Role.CASHIER, accra)).andExpect(status().isNotFound());
    }

    @Test
    void swap_exchangeApprovedByManager_reassignsBothShifts() throws Exception {
        UserEntity colleagueUser = staff("Kojo Colleague", Role.CASHIER, accra);
        UUID colleague = createEmployee(admin, "Kojo", "Colleague", accra);
        postJson("/hr/employees/" + colleague + "/link-user", Map.of("userId", colleagueUser.getId()), admin).andExpect(status().isOk());
        String mine = createShift(manager, shift(cashierEmployee, monday, 9, 17));
        String theirs = createShift(manager, shift(colleague, monday.plusDays(1), 9, 17));
        publishWeek(manager);

        getJson("/hr/me/swap-options?scheduleId=" + mine, cashier).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Kojo Colleague')].shifts[0].id").value(theirs));
        String swapId = json(postJson("/hr/me/swaps", Map.of("scheduleId", mine, "colleagueEmployeeId", colleague,
                "colleagueScheduleId", theirs, "reason", "Family event"), cashier).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/me/swaps", Map.of("scheduleId", mine, "colleagueEmployeeId", colleague, "reason", "Again"), cashier)
                .andExpect(status().isConflict());
        postJson("/hr/shift-swaps/" + swapId + "/decision", Map.of("approve", true), cashier).andExpect(status().isForbidden());
        getJson("/hr/shift-swaps?status=PENDING", manager).andExpect(jsonPath("$.length()").value(1));

        postJson("/hr/shift-swaps/" + swapId + "/decision", Map.of("approve", true, "note", "Fine"), manager)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(workScheduleRepository.findById(UUID.fromString(mine)).orElseThrow().getEmployeeId()).isEqualTo(colleague);
        assertThat(workScheduleRepository.findById(UUID.fromString(mine)).orElseThrow().getUserId()).isEqualTo(colleagueUser.getId());
        assertThat(workScheduleRepository.findById(UUID.fromString(theirs)).orElseThrow().getEmployeeId()).isEqualTo(cashierEmployee);
        getJson("/hr/me/swaps", colleagueUser).andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void swap_isRefusedWhenTheColleagueCannotWork_andPartiesCannotApprove() throws Exception {
        UUID kumasiColleague = createEmployee(admin, "Ama", "Kumasi", kumasi);
        String mine = createShift(manager, shift(cashierEmployee, monday, 9, 17));
        publishWeek(manager);
        postJson("/hr/me/swaps", Map.of("scheduleId", mine, "colleagueEmployeeId", kumasiColleague, "reason", "x"), cashier)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.issues[0].code").value("NOT_AT_BRANCH"));

        // the branch manager is also on the rota and is the colleague: they may not approve it themselves
        UUID managerEmployee = createEmployee(admin, "Accra", "Manager", accra);
        postJson("/hr/employees/" + managerEmployee + "/link-user", Map.of("userId", manager.getId()), admin).andExpect(status().isOk());
        String swapId = json(postJson("/hr/me/swaps", Map.of("scheduleId", mine, "colleagueEmployeeId", managerEmployee, "reason", "Sick child"), cashier)
                .andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/shift-swaps/" + swapId + "/decision", Map.of("approve", true), manager).andExpect(status().isForbidden());
        postJson("/hr/shift-swaps/" + swapId + "/decision", Map.of("approve", false, "note", "Please find someone else"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(workScheduleRepository.findById(UUID.fromString(mine)).orElseThrow().getEmployeeId()).isEqualTo(cashierEmployee);
    }

    @Test
    void branchPolicyOverridesDefaults_andOnlySettingsManagersChangeIt() throws Exception {
        putJson("/hr/scheduling-policy", Map.of("branchId", accra.getId(), "minRestMinutes", 600, "maxShiftMinutes", 600, "maxWeeklyMinutes", 2400), manager)
                .andExpect(status().isForbidden());
        putJson("/hr/scheduling-policy", Map.of("branchId", accra.getId(), "minRestMinutes", 600, "maxShiftMinutes", 600, "maxWeeklyMinutes", 2400), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("BRANCH"));
        getJson("/hr/scheduling-policy?branchId=" + kumasi.getId(), admin).andExpect(jsonPath("$.source").value("CONFIGURATION"));
        // 11h shift: fine under the default 12h limit, a warning under the branch's 10h limit
        postJson("/hr/schedules", shift(cook, monday, 8, 19), manager)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.issues[0].code").value("LONG_SHIFT"));
    }

    @Test
    void legacyRota_attachesEmployeeAndPublishesImmediately() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/scheduling/shifts")
                        .header("Authorization", bearer(manager))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", cashier.getId(), "startsAt", at(monday, 9).toString(),
                                "endsAt", at(monday, 17).toString()))))
                .andExpect(status().isCreated());
        var legacy = workScheduleRepository.findAll().get(0);
        assertThat(legacy.getEmployeeId()).isEqualTo(cashierEmployee);
        assertThat(legacy.getStatus()).isEqualTo(ScheduleStatus.PUBLISHED);
        // an HR draft for the same person blocks a conflicting legacy entry
        createShift(manager, shift(cashierEmployee, monday.plusDays(1), 9, 17));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/scheduling/shifts")
                        .header("Authorization", bearer(manager))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", cashier.getId(), "startsAt", at(monday.plusDays(1), 12).toString(),
                                "endsAt", at(monday.plusDays(1), 20).toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    void legacyShiftsCreatedBeforeLinking_countForOverlapAfterLinking() throws Exception {
        UserEntity late = staff("Late Link", Role.CASHIER, accra);
        // The quick rota now refuses people without an employee record...
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/scheduling/shifts")
                        .header("Authorization", bearer(manager))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", late.getId(), "startsAt", at(monday, 9).toString(),
                                "endsAt", at(monday, 17).toString()))))
                .andExpect(status().isConflict());
        // ...but shifts it made before that (login only, no employee) still count once the login is linked.
        com.restaurantmanager.core.workforce.WorkScheduleEntity legacy = new com.restaurantmanager.core.workforce.WorkScheduleEntity();
        legacy.setUserId(late.getId());
        legacy.setBranchId(accra.getId());
        legacy.setStartsAt(at(monday, 9));
        legacy.setEndsAt(at(monday, 17));
        legacy.setStatus(com.restaurantmanager.core.workforce.ScheduleStatus.PUBLISHED);
        workScheduleRepository.save(legacy);
        UUID employee = createEmployee(admin, "Late", "Link", accra);
        postJson("/hr/employees/" + employee + "/link-user", Map.of("userId", late.getId()), admin).andExpect(status().isOk());
        postJson("/hr/schedules", shift(employee, monday, 12, 20), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.issues[0].code").value("OVERLAP"));
    }
}
