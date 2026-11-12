package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrDashboardIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity hr;

    @BeforeEach
    void setUpData() {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        hr = staff("HR Officer", Role.HR, null);
    }

    @Test
    void dashboard_countsTheWorkforceInScope_andHidesPayFromManagers() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        String ama = createEmployee(admin, "Ama", "Owusu", accra).toString();
        Map<String, Object> starter = employeeBody("Yaa", "Starter", accra);
        starter.put("hireDate", today.plusDays(10).toString());
        postJson("/hr/employees", starter, admin).andExpect(status().isCreated());
        createEmployee(admin, "Kofi", "Mensah", kumasi);
        createEmployee(admin, "Akosua", "Central", null);

        // a fixed-term contract ending within the month shows up under "expiring"
        Map<String, Object> contract = Map.of("contractType", "FIXED_TERM", "startDate", "2025-01-15", "endDate", today.plusDays(10).toString(),
                "salaryType", "MONTHLY", "salaryAmount", 2500, "weeklyHours", 40);
        String contractId = json(postJson("/hr/employees/" + ama + "/contracts", contract, admin).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + contractId + "/activate", Map.of(), admin).andExpect(status().isOk());

        JsonNode mine = json(getJson("/hr/dashboard", manager).andExpect(status().isOk()));
        assertThat(mine.at("/headcount/employed").asLong()).isEqualTo(1);
        assertThat(mine.at("/headcount/startingSoon").asLong()).isEqualTo(1);
        assertThat(mine.get("payVisible").asBoolean()).isFalse();
        assertThat(mine.get("labour")).isEmpty();
        assertThat(mine.get("trend")).hasSize(6);
        assertThat(mine.get("today")).isNotNull(); // managers see attendance
        assertThat(keys(mine.get("actions"))).contains("LEAVE_PENDING", "TIMESHEETS_TO_REVIEW").doesNotContain("PAYROLL_TO_APPROVE");
        assertThat(mine.get("expiring").findValuesAsText("kind")).contains("CONTRACT_END");
        assertThat(mine.get("branches")).hasSize(1);
        getJson("/hr/dashboard?branchId=" + kumasi.getId(), manager).andExpect(status().isForbidden());

        JsonNode all = json(getJson("/hr/dashboard", hr).andExpect(status().isOk()));
        assertThat(all.at("/headcount/employed").asLong()).isEqualTo(3); // Accra, Kumasi and the central employee
        assertThat(all.get("payVisible").asBoolean()).isTrue();
        assertThat(keys(all.get("actions"))).contains("PAYROLL_TO_APPROVE", "POOLS_TO_APPROVE");
        assertThat(all.at("/recruitment/openVacancies").asLong()).isZero();

        JsonNode kumasiOnly = json(getJson("/hr/dashboard?branchId=" + kumasi.getId(), admin).andExpect(status().isOk()));
        assertThat(kumasiOnly.at("/headcount/employed").asLong()).isEqualTo(1);

        // no hr.reports.view: no dashboard
        getJson("/hr/dashboard", staff("Rider", Role.RIDER, accra)).andExpect(status().isForbidden());
    }

    private static List<String> keys(JsonNode actions) {
        List<String> keys = new ArrayList<>();
        actions.forEach(a -> keys.add(a.get("key").asText()));
        return keys;
    }
}
