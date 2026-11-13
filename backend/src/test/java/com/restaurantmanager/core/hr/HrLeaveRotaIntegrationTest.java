package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.leave.LeaveService;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Days counted from the rota, approval clearing the rota, yearly caps, status, carry-over, reminders and New Year leave. */
class HrLeaveRotaIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    @Autowired
    private WorkScheduleRepository scheduleRepository;
    @Autowired
    private LeaveService leaveService;

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity cookUser;
    private UUID cook;
    private String annual;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("LRA");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cookUser = staff("Kwesi Cook", Role.CASHIER, accra);
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        postJson("/hr/employees/" + cook + "/link-user", Map.of("userId", cookUser.getId()), admin).andExpect(status().isOk());
        annual = type("ANNUAL", true, null);
        policy(annual, 20, 5);
    }

    private String type(String code, boolean deducts, Integer maxPerYear) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", code.charAt(0) + code.substring(1).toLowerCase() + " leave");
        body.put("paid", true);
        body.put("deductsBalance", deducts);
        body.put("requiresDocument", false);
        body.put("allowNegative", false);
        body.put("countBasis", "WORKDAYS");
        body.put("approvalSteps", List.of("BRANCH_MANAGER"));
        body.put("maxDaysPerYear", maxPerYear);
        return json(postJson("/hr/leave-types", body, admin).andExpect(status().isCreated())).get("id").asText();
    }

    private void policy(String typeId, int days, int carry) throws Exception {
        postJson("/hr/leave-policies", Map.of("leaveTypeId", typeId, "annualDays", days, "accrual", "UPFRONT", "carryOverMaxDays", carry,
                "effectiveFrom", "2020-01-01"), admin).andExpect(status().isCreated());
    }

    private Map<String, Object> leave(String typeId, LocalDate from, LocalDate to) {
        Map<String, Object> body = new HashMap<>();
        body.put("leaveTypeId", typeId);
        body.put("startDate", from.toString());
        body.put("endDate", to.toString());
        return body;
    }

    private UUID shift(LocalDate day) {
        WorkScheduleEntity s = new WorkScheduleEntity();
        s.setEmployeeId(cook);
        s.setUserId(cookUser.getId());
        s.setBranchId(accra.getId());
        s.setStartsAt(day.atTime(10, 0).atZone(ZONE).toInstant());
        s.setEndsAt(day.atTime(18, 0).atZone(ZONE).toInstant());
        s.setStatus(ScheduleStatus.PUBLISHED);
        s.setPublishedAt(Instant.now());
        return scheduleRepository.save(s).getId();
    }

    private JsonNode quote(LocalDate from, LocalDate to) throws Exception {
        return json(postJson("/hr/me/leave-requests/quote", leave(annual, from, to), cookUser).andExpect(status().isOk()));
    }

    @Test
    void weekendWorker_isChargedForTheDaysOnTheRota_andApprovalOpensTheShifts() throws Exception {
        LocalDate monday = LocalDate.now(ZONE).plusWeeks(2).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UUID fri = shift(monday.plusDays(4));
        shift(monday.plusDays(5));
        shift(monday.plusDays(6));

        JsonNode q = quote(monday, monday.plusDays(6));
        assertThat(q.get("days").decimalValue()).isEqualByComparingTo("3");
        assertThat(q.get("countedFrom").asText()).isEqualTo("ROTA");

        String id = json(postJson("/hr/me/leave-requests", leave(annual, monday, monday.plusDays(6)), cookUser).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), manager).andExpect(status().isOk());
        WorkScheduleEntity released = scheduleRepository.findById(fri).orElseThrow();
        assertThat(released.getEmployeeId()).isNull();
        assertThat(released.getNotes()).contains("on leave");
    }

    @Test
    void withoutARota_theUsualPatternCounts() throws Exception {
        LocalDate monday = LocalDate.now(ZONE).plusWeeks(3).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int w = 1; w <= 3; w++) {
            shift(monday.minusWeeks(w).plusDays(5)); // Saturdays
            shift(monday.minusWeeks(w).plusDays(6)); // Sundays
        }
        JsonNode q = quote(monday, monday.plusDays(6));
        assertThat(q.get("days").decimalValue()).isEqualByComparingTo("2");
        assertThat(q.get("countedFrom").asText()).isEqualTo("USUAL_PATTERN");
    }

    @Test
    void typesWithAYearlyLimit_refuseMoreDays() throws Exception {
        String compassionate = type("COMPASSIONATE", false, 3);
        LocalDate monday = LocalDate.of(LocalDate.now(ZONE).getYear() + 1, 3, 2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        postJson("/hr/me/leave-requests", leave(compassionate, monday, monday.plusDays(1)), cookUser).andExpect(status().isCreated());
        postJson("/hr/me/leave-requests", leave(compassionate, monday.plusDays(7), monday.plusDays(8)), cookUser)
                .andExpect(status().isConflict());
        postJson("/hr/me/leave-requests", leave(compassionate, monday.plusDays(7), monday.plusDays(7)), cookUser)
                .andExpect(status().isCreated());
    }

    @Test
    void approvedLeaveCoveringToday_setsOnLeave_andCancellingRestoresActive() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Object> body = leave(annual, today, today.plusDays(7));
        String id = json(postJson("/hr/me/leave-requests", body, cookUser).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/leave-requests/" + id + "/decision", Map.of("approve", true), manager).andExpect(status().isOk());
        assertThat(json(getJson("/hr/employees/" + cook, admin)).at("/summary/employmentStatus").asText()).isEqualTo("ON_LEAVE");

        postJson("/hr/leave-requests/" + id + "/cancel", Map.of("reason", "Needed back"), manager).andExpect(status().isOk());
        assertThat(json(getJson("/hr/employees/" + cook, admin)).at("/summary/employmentStatus").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void carryOver_followsLateChangesToThePreviousYear() throws Exception {
        int year = LocalDate.now(ZONE).getYear();
        postJson("/hr/employees/" + cook + "/leave-balances/adjust", Map.of("leaveTypeId", annual, "year", year + 1, "days", 1, "note", "Open"), admin)
                .andExpect(status().isOk());
        postJson("/hr/employees/" + cook + "/leave-balances/adjust", Map.of("leaveTypeId", annual, "year", year, "days", -18, "note", "Taken"), admin)
                .andExpect(status().isOk());
        JsonNode next = null;
        for (JsonNode b : json(getJson("/hr/employees/" + cook + "/leave-balances?year=" + (year + 1), admin))) {
            if (b.get("leaveTypeCode").asText().equals("ANNUAL")) next = b;
        }
        assertThat(next).isNotNull();
        assertThat(next.get("carriedOverDays").decimalValue()).isEqualByComparingTo("2");
    }

    @Test
    void pendingRequestsStartingSoon_remindApprovers() throws Exception {
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);
        postJson("/hr/me/leave-requests", leave(annual, tomorrow, tomorrow.plusDays(4)), cookUser).andExpect(status().isCreated());
        assertThat(leaveService.remindPending()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void leaveOverNewYear_chargesEachYearsBalance() throws Exception {
        int year = LocalDate.now(ZONE).getYear() + 1;
        JsonNode created = json(postJson("/hr/me/leave-requests", leave(annual, LocalDate.of(year, 12, 29), LocalDate.of(year + 1, 1, 9)), cookUser)
                .andExpect(status().isCreated()));
        java.math.BigDecimal days = created.get("days").decimalValue();
        java.math.BigDecimal pendingThis = java.math.BigDecimal.ZERO;
        java.math.BigDecimal pendingNext = java.math.BigDecimal.ZERO;
        for (JsonNode b : json(getJson("/hr/employees/" + cook + "/leave-balances?year=" + year, admin))) {
            if (b.get("leaveTypeCode").asText().equals("ANNUAL")) pendingThis = b.get("pendingDays").decimalValue();
        }
        for (JsonNode b : json(getJson("/hr/employees/" + cook + "/leave-balances?year=" + (year + 1), admin))) {
            if (b.get("leaveTypeCode").asText().equals("ANNUAL")) pendingNext = b.get("pendingDays").decimalValue();
        }
        assertThat(pendingThis).isPositive();
        assertThat(pendingNext).isPositive();
        assertThat(pendingThis.add(pendingNext)).isEqualByComparingTo(days);
    }
}
