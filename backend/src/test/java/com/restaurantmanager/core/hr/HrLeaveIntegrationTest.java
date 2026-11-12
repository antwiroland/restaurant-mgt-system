package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrLeaveIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");
    private static final byte[] PDF = "%PDF-1.4\n%sick note\n".getBytes(StandardCharsets.US_ASCII);

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity kumasiManager;
    private UserEntity cookUser;
    private UUID cook;
    /** A Monday in March next year: always in the future and never crossing a year end within the week. */
    private LocalDate monday;
    private int year;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        cookUser = staff("Kwesi Cook", Role.CASHIER, accra);
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        postJson("/hr/employees/" + cook + "/link-user", Map.of("userId", cookUser.getId()), admin).andExpect(status().isOk());
        monday = LocalDate.of(LocalDate.now(ZONE).getYear() + 1, 3, 2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        year = monday.getYear();
    }

    // ------------------------------------------------------------ helpers

    private String leaveType(String code, List<String> steps, boolean deducts, boolean requiresDocument, boolean allowNegative) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", code.charAt(0) + code.substring(1).toLowerCase() + " leave");
        body.put("paid", true);
        body.put("deductsBalance", deducts);
        body.put("requiresDocument", requiresDocument);
        body.put("allowNegative", allowNegative);
        body.put("countBasis", "WORKDAYS");
        body.put("approvalSteps", steps);
        return json(postJson("/hr/leave-types", body, admin).andExpect(status().isCreated())).get("id").asText();
    }

    private void policy(String typeId, int annualDays, int carryOver) throws Exception {
        postJson("/hr/leave-policies", Map.of("leaveTypeId", typeId, "annualDays", annualDays, "accrual", "UPFRONT",
                "carryOverMaxDays", carryOver, "effectiveFrom", "2020-01-01"), admin).andExpect(status().isCreated());
    }

    private Map<String, Object> leave(String typeId, LocalDate from, LocalDate to) {
        Map<String, Object> body = new HashMap<>();
        body.put("leaveTypeId", typeId);
        body.put("startDate", from.toString());
        body.put("endDate", to.toString());
        return body;
    }

    private String requestMine(UserEntity user, String typeId, LocalDate from, LocalDate to) throws Exception {
        return json(postJson("/hr/me/leave-requests", leave(typeId, from, to), user).andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode decide(String id, UserEntity user, boolean approve, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("approve", approve);
        body.put("note", note);
        return json(postJson("/hr/leave-requests/" + id + "/decision", body, user));
    }

    private JsonNode balance(UserEntity user, String typeCode, int forYear) throws Exception {
        for (JsonNode b : json(getJson("/hr/me/leave-balances?year=" + forYear, user).andExpect(status().isOk()))) {
            if (b.get("leaveTypeCode").asText().equals(typeCode)) {
                return b;
            }
        }
        throw new AssertionError("No balance for " + typeCode);
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private Map<String, Object> shift(UUID employee, LocalDate day) {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employee);
        body.put("branchId", accra.getId());
        body.put("startsAt", at(day, 9).toString());
        body.put("endsAt", at(day, 17).toString());
        body.put("breakMinutes", 30);
        return body;
    }

    // ------------------------------------------------------------ tests

    @Test
    void selfServiceRequest_reservesThenUsesBalance_andCancellingRestoresIt() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER"), true, false, false);
        policy(annual, 20, 5);

        JsonNode quote = json(postJson("/hr/me/leave-requests/quote", leave(annual, monday, monday.plusDays(6)), cookUser).andExpect(status().isOk()));
        assertThat(quote.get("days").decimalValue()).isEqualByComparingTo("5");
        assertThat(quote.get("availableDays").decimalValue()).isEqualByComparingTo("20");
        assertThat(quote.get("problems")).isEmpty();

        String id = requestMine(cookUser, annual, monday, monday.plusDays(4));
        assertThat(balance(cookUser, "ANNUAL", year).get("pendingDays").decimalValue()).isEqualByComparingTo("5");
        assertThat(balance(cookUser, "ANNUAL", year).get("availableDays").decimalValue()).isEqualByComparingTo("15");

        postJson("/hr/me/leave-requests", leave(annual, monday.plusDays(2), monday.plusDays(3)), cookUser).andExpect(status().isConflict());
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), cookUser).andExpect(status().isForbidden());
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), kumasiManager).andExpect(status().isNotFound());

        JsonNode awaiting = json(getJson("/hr/leave-requests?awaitingMe=true", manager).andExpect(status().isOk()));
        assertThat(awaiting).hasSize(1);
        assertThat(awaiting.get(0).get("canDecide").asBoolean()).isTrue();
        assertThat(awaiting.get(0).get("pendingStep").asText()).isEqualTo("BRANCH_MANAGER");

        assertThat(decide(id, manager, true, null).get("status").asText()).isEqualTo("APPROVED");
        JsonNode after = balance(cookUser, "ANNUAL", year);
        assertThat(after.get("usedDays").decimalValue()).isEqualByComparingTo("5");
        assertThat(after.get("pendingDays").decimalValue()).isZero();
        assertThat(after.get("availableDays").decimalValue()).isEqualByComparingTo("15");

        postJson("/hr/me/leave-requests/" + id + "/cancel", Map.of("reason", "Plans changed"), cookUser)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        JsonNode restored = balance(cookUser, "ANNUAL", year);
        assertThat(restored.get("availableDays").decimalValue()).isEqualByComparingTo("20");
        List<String> kinds = new ArrayList<>();
        json(getJson("/hr/leave-balances/" + restored.get("id").asText() + "/transactions", cookUser).andExpect(status().isOk()))
                .forEach(t -> kinds.add(t.get("kind").asText()));
        assertThat(kinds).containsExactly("ENTITLEMENT", "PENDING", "RELEASE", "USE", "RESTORE");

        assertThat(auditLogRepository.findByAction(AuditAction.HR_LEAVE_REQUESTED)).hasSize(1);
        assertThat(auditLogRepository.findByAction(AuditAction.HR_LEAVE_DECIDED)).hasSize(1);
        assertThat(auditLogRepository.findByAction(AuditAction.HR_LEAVE_CANCELLED)).hasSize(1);
    }

    @Test
    void insufficientBalance_isRefused_unlessTheTypeAllowsNegative() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER"), true, false, false);
        policy(annual, 3, 0);
        postJson("/hr/me/leave-requests", leave(annual, monday, monday.plusDays(4)), cookUser)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Only 3 days of annual leave are available"));
        assertThat(json(postJson("/hr/me/leave-requests/quote", leave(annual, monday, monday.plusDays(4)), cookUser)).get("problems").get(0).asText())
                .isEqualTo("Only 3 days available");

        String sick = leaveType("SICK", List.of("BRANCH_MANAGER"), true, false, true);
        policy(sick, 2, 0);
        requestMine(cookUser, sick, monday, monday.plusDays(4));
        assertThat(balance(cookUser, "SICK", year).get("availableDays").decimalValue()).isEqualByComparingTo("-3");
    }

    @Test
    void multiStepChain_needsADifferentPersonPerStep_andDecliningNeedsAReason() throws Exception {
        String study = leaveType("STUDY", List.of("BRANCH_MANAGER", "HR"), false, false, false);
        String id = requestMine(cookUser, study, monday, monday);

        JsonNode first = decide(id, manager, true, "Fine by the branch");
        assertThat(first.get("status").asText()).isEqualTo("PENDING");
        assertThat(first.get("pendingStep").asText()).isEqualTo("HR");
        assertThat(first.at("/steps/0/decision").asText()).isEqualTo("APPROVED");
        assertThat(first.at("/steps/0/decidedByName").asText()).isEqualTo("Accra Manager");
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), manager).andExpect(status().isForbidden());

        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", false), admin).andExpect(status().isBadRequest());
        assertThat(decide(id, admin, false, "Exam dates not confirmed").get("status").asText()).isEqualTo("REJECTED");
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), admin).andExpect(status().isConflict());

        String second = requestMine(cookUser, study, monday.plusDays(1), monday.plusDays(1));
        decide(second, manager, true, null);
        assertThat(decide(second, admin, true, null).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void requiredDocument_mustBeAttachedBeforeFinalApproval() throws Exception {
        String sick = leaveType("SICK", List.of("BRANCH_MANAGER"), false, true, false);
        String id = requestMine(cookUser, sick, monday, monday.plusDays(1));
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Sick leave needs a supporting document before it can be approved"));

        mockMvc.perform(multipart("/hr/me/leave-requests/" + id + "/document")
                        .file(new MockMultipartFile("file", "note.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer(cookUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").isNotEmpty());
        assertThat(decide(id, manager, true, null).get("status").asText()).isEqualTo("APPROVED");

        JsonNode documents = json(getJson("/hr/employees/" + cook + "/documents", admin).andExpect(status().isOk()));
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).get("documentType").asText()).isEqualTo("LEAVE_SUPPORT");
    }

    @Test
    void reportingManagerStep_isDecidedByTheLinkedManager_andSkippedWithoutOne() throws Exception {
        String compassionate = leaveType("COMPASSIONATE", List.of("REPORTING_MANAGER", "BRANCH_MANAGER"), false, false, false);
        UserEntity supervisorUser = staff("Ama Supervisor", Role.CASHIER, accra);
        UUID supervisor = createEmployee(admin, "Ama", "Supervisor", accra);
        postJson("/hr/employees/" + supervisor + "/link-user", Map.of("userId", supervisorUser.getId()), admin).andExpect(status().isOk());

        // no reporting manager yet: the step is dropped
        String direct = requestMine(cookUser, compassionate, monday, monday);
        JsonNode directView = json(getJson("/hr/me/leave-requests", cookUser)).get(0);
        assertThat(directView.get("steps")).hasSize(1);
        assertThat(directView.get("pendingStep").asText()).isEqualTo("BRANCH_MANAGER");
        postJson("/hr/me/leave-requests/" + direct + "/cancel", Map.of(), cookUser).andExpect(status().isOk());

        Map<String, Object> update = new HashMap<>(employeeBody("Kwesi", "Cook", accra));
        update.put("reportingManagerId", supervisor);
        putJson("/hr/employees/" + cook, update, admin).andExpect(status().isOk());

        String id = requestMine(cookUser, compassionate, monday.plusDays(1), monday.plusDays(1));
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), manager)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("This step waits for the reporting manager"));
        JsonNode awaiting = json(getJson("/hr/leave-requests?awaitingMe=true", supervisorUser).andExpect(status().isOk()));
        assertThat(awaiting).hasSize(1);
        getJson("/hr/leave-requests?status=PENDING", supervisorUser).andExpect(status().isForbidden()); // no general leave access

        assertThat(decide(id, supervisorUser, true, null).get("pendingStep").asText()).isEqualTo("BRANCH_MANAGER");
        assertThat(decide(id, manager, true, null).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void approvedLeaveBlocksScheduling_pendingLeaveOnlyWarns() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER"), true, false, false);
        policy(annual, 20, 0);
        String id = requestMine(cookUser, annual, monday, monday.plusDays(4));

        postJson("/hr/schedules", shift(cook, monday), manager)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.issues[0].code").value("LEAVE_PENDING"));
        Map<String, Object> acknowledged = shift(cook, monday);
        acknowledged.put("acknowledgeWarnings", true);
        postJson("/hr/schedules", acknowledged, manager).andExpect(status().isCreated());

        JsonNode approved = decide(id, manager, true, null);
        assertThat(approved.get("conflictingShifts")).hasSize(1);
        postJson("/hr/schedules", shift(cook, monday.plusDays(1)), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.issues[0].code").value("ON_APPROVED_LEAVE"));
        // the weekend after is free
        postJson("/hr/schedules", shift(cook, monday.plusDays(5)), manager).andExpect(status().isCreated());

        JsonNode calendar = json(getJson("/hr/leave-calendar?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(6), manager)
                .andExpect(status().isOk()));
        assertThat(calendar).hasSize(1);
        getJson("/hr/leave-calendar?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(6), kumasiManager)
                .andExpect(status().isForbidden());
    }

    @Test
    void workdayCount_skipsHolidaysAndCountsHalfDays() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER"), true, false, false);
        policy(annual, 20, 0);
        postJson("/hr/holidays", Map.of("date", monday.plusDays(2).toString(), "name", "Founders' Day"), admin).andExpect(status().isCreated());
        Map<String, Object> body = leave(annual, monday, monday.plusDays(4));
        body.put("endHalf", true);
        String id = json(postJson("/hr/me/leave-requests", body, cookUser).andExpect(status().isCreated())).get("id").asText();
        assertThat(json(getJson("/hr/leave-requests/" + id, manager)).get("days").decimalValue()).isEqualByComparingTo("3.5");

        Map<String, Object> weekend = leave(annual, monday.plusDays(5), monday.plusDays(6));
        postJson("/hr/me/leave-requests", weekend, cookUser)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The selected dates contain no working days"));
        Map<String, Object> acrossYears = leave(annual, LocalDate.of(year, 12, 30), LocalDate.of(year + 1, 1, 2));
        postJson("/hr/me/leave-requests", acrossYears, cookUser).andExpect(status().isBadRequest());
    }

    @Test
    void carryOver_isCappedByPolicy_andOnlyHrAdjustsBalances() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER"), true, false, false);
        policy(annual, 20, 5);
        int lastYear = year - 1;
        postJson("/hr/employees/" + cook + "/leave-balances/adjust",
                Map.of("leaveTypeId", annual, "year", lastYear, "days", -12, "note", "Days taken before HR went live"), manager)
                .andExpect(status().isForbidden());
        postJson("/hr/employees/" + cook + "/leave-balances/adjust",
                Map.of("leaveTypeId", annual, "year", lastYear, "days", -12, "note", "Days taken before HR went live"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableDays").value(8.0));

        JsonNode next = balance(cookUser, "ANNUAL", year);
        assertThat(next.get("carriedOverDays").decimalValue()).isEqualByComparingTo("5");
        assertThat(next.get("availableDays").decimalValue()).isEqualByComparingTo("25");

        postJson("/hr/employees/" + cook + "/leave-balances/adjust",
                Map.of("leaveTypeId", annual, "year", year, "days", -30, "note", "Too much"), admin).andExpect(status().isConflict());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_LEAVE_BALANCE_ADJUSTED)).hasSize(1);
    }

    @Test
    void hrRecordsApprovedLeave_timesheetShowsLeaveInsteadOfAbsence() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER", "HR"), true, false, false);
        policy(annual, 20, 0);
        LocalDate lastMonday = LocalDate.now(ZONE).minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate wednesday = lastMonday.plusDays(2);
        WorkScheduleEntity s = new WorkScheduleEntity();
        s.setEmployeeId(cook);
        s.setBranchId(accra.getId());
        s.setStartsAt(at(wednesday, 9));
        s.setEndsAt(at(wednesday, 17));
        s.setBreakMinutes(30);
        s.setStatus(ScheduleStatus.PUBLISHED);
        workScheduleRepository.save(s);

        Map<String, Object> body = leave(annual, wednesday, wednesday);
        body.put("employeeId", cook);
        body.put("recordApproved", true);
        postJson("/hr/leave-requests", body, manager).andExpect(status().isForbidden());
        postJson("/hr/leave-requests", body, admin).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));

        postJson("/hr/timesheets/generate", Map.of("branchId", accra.getId(), "from", lastMonday.toString(), "to", lastMonday.plusDays(6).toString()), manager)
                .andExpect(status().isOk());
        String sheet = json(getJson("/hr/timesheets?branchId=" + accra.getId() + "&from=" + lastMonday + "&to=" + lastMonday.plusDays(6), manager))
                .get(0).get("id").asText();
        JsonNode detail = json(getJson("/hr/timesheets/" + sheet, manager));
        assertThat(detail.at("/summary/absenceCount").asInt()).isZero();
        assertThat(detail.at("/lines/0/flags/0").asText()).isEqualTo("LEAVE");
        assertThat(detail.at("/lines/0/flags/1").asText()).isEqualTo("ANNUAL");

        getJson("/hr/attendance/day?branchId=" + accra.getId() + "&date=" + wednesday, manager)
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].status").value("ON_LEAVE"));
    }

    @Test
    void branchIsolation_andCentralConfiguration() throws Exception {
        String annual = leaveType("ANNUAL", List.of("BRANCH_MANAGER"), false, false, false);
        String id = requestMine(cookUser, annual, monday, monday);
        assertThat(json(getJson("/hr/leave-requests", kumasiManager).andExpect(status().isOk()))).isEmpty();
        assertThat(json(getJson("/hr/leave-requests", manager).andExpect(status().isOk()))).hasSize(1);
        getJson("/hr/leave-requests/" + id, kumasiManager).andExpect(status().isNotFound());
        getJson("/hr/leave-requests/" + id, cookUser).andExpect(status().isOk());
        assertThat(json(getJson("/hr/leave-requests?employeeId=" + cook, manager).andExpect(status().isOk()))).hasSize(1);
        getJson("/hr/leave-requests?employeeId=" + cook, kumasiManager).andExpect(status().isNotFound());

        Map<String, Object> type = new HashMap<>();
        type.put("code", "BIRTHDAY");
        type.put("name", "Birthday");
        type.put("countBasis", "WORKDAYS");
        type.put("approvalSteps", List.of("BRANCH_MANAGER"));
        postJson("/hr/leave-types", type, manager).andExpect(status().isForbidden());
        postJson("/hr/leave-policies", Map.of("leaveTypeId", annual, "annualDays", 5, "accrual", "UPFRONT", "effectiveFrom", "2020-01-01"), manager)
                .andExpect(status().isForbidden());
        type.put("code", "ANNUAL");
        postJson("/hr/leave-types", type, admin).andExpect(status().isConflict());
        // inactive types are hidden from staff
        type.put("name", "Annual leave");
        type.put("active", false);
        putJson("/hr/leave-types/" + annual, type, admin).andExpect(status().isOk());
        assertThat(json(getJson("/hr/leave-types", cookUser))).isEmpty();
        assertThat(json(getJson("/hr/leave-types", admin))).hasSize(1);
    }
}
