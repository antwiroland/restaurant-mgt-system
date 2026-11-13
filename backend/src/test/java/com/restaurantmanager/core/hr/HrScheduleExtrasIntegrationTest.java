package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
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

/** Copy a week, publish one shift, open shifts staff pick up, and the labour cost estimate on the board. */
class HrScheduleExtrasIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity cashier;
    private UUID cashierEmployee;
    private UUID cook;
    private LocalDate monday;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("XAC");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cashier = staff("Efua Cashier", Role.CASHIER, accra);
        cashierEmployee = createEmployee(admin, "Efua", "Cashier", accra);
        postJson("/hr/employees/" + cashierEmployee + "/link-user", Map.of("userId", cashier.getId()), admin).andExpect(status().isOk());
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        monday = LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Test
    void copyWeek_makesDraftsAWeekLater_andSkipsWhatWouldClash() throws Exception {
        draft(cashierEmployee, monday, 9, 17);
        draft(cook, monday.plusDays(2), 12, 20);
        // The cook already works the Wednesday of the target week: that copy must be skipped.
        draft(cook, monday.plusDays(9), 10, 18);

        JsonNode result = json(postJson("/hr/schedules/copy-week",
                Map.of("branchId", accra.getId(), "fromWeek", monday.toString(), "toWeek", monday.plusWeeks(1).toString(), "includeOpenShifts", true),
                manager).andExpect(status().isOk()));
        assertThat(result.get("copied").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").get(0).get("employeeName").asText()).isEqualTo("Kwesi Cook");

        JsonNode board = json(getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday.plusWeeks(1) + "&to=" + monday.plusWeeks(1).plusDays(6), manager));
        long drafts = 0;
        for (JsonNode shift : board.get("shifts")) {
            if (shift.get("status").asText().equals("DRAFT") && shift.get("employeeId").asText().equals(cashierEmployee.toString())) {
                drafts++;
                assertThat(Instant.parse(shift.get("startsAt").asText())).isEqualTo(at(monday.plusWeeks(1), 9));
            }
        }
        assertThat(drafts).isEqualTo(1);
    }

    @Test
    void publishOne_publishesJustThatDraft() throws Exception {
        UUID first = draft(cashierEmployee, monday, 9, 17);
        UUID second = draft(cashierEmployee, monday.plusDays(1), 9, 17);
        postJson("/hr/schedules/" + first + "/publish", Map.of(), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        postJson("/hr/schedules/" + first + "/publish", Map.of(), manager).andExpect(status().isConflict());
        JsonNode board = json(getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(6), manager));
        for (JsonNode shift : board.get("shifts")) {
            if (shift.get("id").asText().equals(second.toString())) {
                assertThat(shift.get("status").asText()).isEqualTo("DRAFT");
            }
        }
    }

    @Test
    void openShift_canBePickedUpOnce_byStaffAtThatBranch() throws Exception {
        UUID open = draft(null, monday, 17, 23);
        postJson("/hr/schedules/publish", Map.of("branchId", accra.getId(), "from", monday.toString(), "to", monday.toString()), manager)
                .andExpect(status().isOk());

        getJson("/hr/me/open-shifts", cashier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shift.id").value(open.toString()));
        postJson("/hr/me/open-shifts/" + open + "/claim", Map.of(), cashier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(cashierEmployee.toString()));

        getJson("/hr/me/open-shifts", cashier).andExpect(jsonPath("$").isEmpty());
        postJson("/hr/me/open-shifts/" + open + "/claim", Map.of(), cashier).andExpect(status().isConflict());
    }

    @Test
    void board_estimatesLabourCost_withPeopleOnlyForThoseWhoMaySeePay() throws Exception {
        // 2,080 a month on 40 h/week = 2080 x 12 / (52 x 40) = 12.00 an hour
        UUID contract = UUID.fromString(json(postJson("/hr/employees/" + cashierEmployee + "/contracts", contract("2080.00"), admin)
                .andExpect(status().isCreated())).get("id").asText());
        postJson("/hr/contracts/" + contract + "/activate", Map.of(), admin).andExpect(status().isOk());
        draft(cashierEmployee, monday, 9, 17);   // 8 h - 30 min break = 7.5 h -> 90.00
        draft(cook, monday, 9, 17);              // no contract: unpriced
        draft(null, monday, 17, 22);             // open: unpriced

        String board = "/hr/schedules?branchId=" + accra.getId() + "&from=" + monday + "&to=" + monday.plusDays(6);
        getJson(board, manager)
                .andExpect(jsonPath("$.labour.total").value(90.0))
                .andExpect(jsonPath("$.labour.pricedShifts").value(1))
                .andExpect(jsonPath("$.labour.unpricedShifts").value(2))
                .andExpect(jsonPath("$.labour.byEmployee").isEmpty());
        getJson(board, admin)
                .andExpect(jsonPath("$.labour.byEmployee[0].name").value("Efua Cashier"))
                .andExpect(jsonPath("$.labour.byEmployee[0].cost").value(90.0));
    }

    private UUID draft(UUID employee, LocalDate day, int from, int to) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employee);
        body.put("branchId", accra.getId());
        body.put("startsAt", at(day, from).toString());
        body.put("endsAt", at(day, to).toString());
        body.put("breakMinutes", 30);
        body.put("acknowledgeWarnings", true);
        return UUID.fromString(json(postJson("/hr/schedules", body, manager).andExpect(status().isCreated())).at("/shift/id").asText());
    }

    private Map<String, Object> contract(String monthly) {
        Map<String, Object> body = new HashMap<>();
        body.put("contractType", "PERMANENT");
        body.put("startDate", LocalDate.now(ZONE).minusMonths(1).toString());
        body.put("salaryType", "MONTHLY");
        body.put("salaryAmount", monthly);
        body.put("weeklyHours", 40);
        return body;
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }
}
