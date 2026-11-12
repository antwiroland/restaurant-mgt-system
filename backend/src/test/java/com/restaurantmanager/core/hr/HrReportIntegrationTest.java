package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrReportIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;

    @BeforeEach
    void setUpData() {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
    }

    @Test
    void reports_followPermissionsAndBranchScope_andExportAuditedCsv() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        createEmployee(admin, "Ama", "Owusu", accra);
        Map<String, Object> joiner = employeeBody("Yaw", "Joiner", accra);
        joiner.put("hireDate", today.withDayOfMonth(1).toString());
        postJson("/hr/employees", joiner, admin).andExpect(status().isCreated());
        createEmployee(admin, "Kofi", "Mensah", kumasi);

        // managers: workforce, leave and hours reports, never payroll
        assertThat(types(json(getJson("/hr/reports", manager).andExpect(status().isOk())))).containsExactly("EMPLOYEES", "MOVEMENT", "LEAVE", "HOURS");
        assertThat(types(json(getJson("/hr/reports", admin)))).contains("PAYROLL");
        getJson("/hr/reports/PAYROLL", manager).andExpect(status().isForbidden());

        JsonNode register = json(getJson("/hr/reports/EMPLOYEES", manager).andExpect(status().isOk()));
        assertThat(register.get("rows")).hasSize(2); // Accra only
        assertThat(register.get("rows").findValuesAsText("name")).containsExactly("Ama Owusu", "Yaw Joiner");
        assertThat(register.get("columns").findValuesAsText("key")).doesNotContain("salary", "salaryAmount");
        assertThat(json(getJson("/hr/reports/EMPLOYEES", admin)).get("rows")).hasSize(3);
        getJson("/hr/reports/EMPLOYEES?branchId=" + kumasi.getId(), manager).andExpect(status().isForbidden());

        JsonNode movement = json(getJson("/hr/reports/MOVEMENT?from=" + today.withDayOfMonth(1) + "&to=" + today, manager).andExpect(status().isOk()));
        assertThat(movement.get("rows").findValuesAsText("name")).containsExactly("Yaw Joiner");
        assertThat(movement.get("rows").get(0).get("event").asText()).isEqualTo("Joined");

        JsonNode payroll = json(getJson("/hr/reports/PAYROLL", admin).andExpect(status().isOk()));
        assertThat(payroll.get("columns").findValuesAsText("label")).contains("Gross", "Net", "Employer cost");

        // period rules
        getJson("/hr/reports/LEAVE?from=" + today + "&to=" + today.minusDays(1), manager).andExpect(status().isBadRequest());
        getJson("/hr/reports/LEAVE?from=" + today.minusDays(400) + "&to=" + today, manager).andExpect(status().isBadRequest());

        // CSV: BOM, header and one line per row, and the export is audited
        MockHttpServletResponse csv = getJson("/hr/reports/EMPLOYEES/csv", manager).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(csv.getContentType()).startsWith("text/csv");
        assertThat(csv.getHeader("Content-Disposition")).contains("hr-employees.csv");
        String text = new String(csv.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Employee no.,Name,Department");
        assertThat(text.split("\r\n")).hasSize(3);
        assertThat(auditLogRepository.findAll()).anyMatch(a -> a.getAction() == AuditAction.HR_REPORT_EXPORTED);

        // no hr.reports.view: nothing
        getJson("/hr/reports", staff("Rider", Role.RIDER, accra)).andExpect(status().isForbidden());
    }

    private static List<String> types(JsonNode list) {
        List<String> types = new ArrayList<>();
        list.forEach(r -> types.add(r.get("type").asText()));
        return types;
    }
}
