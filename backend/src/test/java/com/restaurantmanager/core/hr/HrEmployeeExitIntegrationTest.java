package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Leaving switches the login off and clears commitments; ID numbers, export, missing-information flags and logins made from HR. */
class HrEmployeeExitIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity cashier;
    private UUID employee;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("XEX");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cashier = staff("Ama Cashier", Role.CASHIER, accra);
        employee = createEmployee(admin, "Ama", "Cashier", accra);
        postJson("/hr/employees/" + employee + "/link-user", Map.of("userId", cashier.getId()), admin).andExpect(status().isOk());
    }

    @Test
    void exit_disablesLogin_releasesShifts_endsContract_andReturnRestoresLogin() throws Exception {
        LocalDate later = LocalDate.now(ZONE).plusDays(5);
        Map<String, Object> shift = new HashMap<>();
        shift.put("employeeId", employee);
        shift.put("branchId", accra.getId());
        shift.put("startsAt", later.atTime(9, 0).atZone(ZONE).toInstant().toString());
        shift.put("endsAt", later.atTime(17, 0).atZone(ZONE).toInstant().toString());
        shift.put("breakMinutes", 30);
        shift.put("acknowledgeWarnings", true);
        String shiftId = json(postJson("/hr/schedules", shift, manager).andExpect(status().isCreated())).at("/shift/id").asText();

        Map<String, Object> contract = new HashMap<>();
        contract.put("contractType", "PERMANENT");
        contract.put("startDate", "2025-01-15");
        contract.put("salaryType", "MONTHLY");
        contract.put("salaryAmount", "2500");
        contract.put("weeklyHours", 40);
        String contractId = json(postJson("/hr/employees/" + employee + "/contracts", contract, admin).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + contractId + "/activate", Map.of(), admin).andExpect(status().isOk());

        String tokenBefore = bearer(cashier);
        JsonNode result = json(postJson("/hr/employees/" + employee + "/status",
                Map.of("status", "RESIGNED", "effectiveDate", LocalDate.now(ZONE).toString(), "reason", "Moved away"), admin)
                .andExpect(status().isOk()));
        assertThat(result.at("/statusEffects/loginDisabled").asBoolean()).isTrue();
        assertThat(result.at("/statusEffects/shiftsReleased").asInt()).isEqualTo(1);
        assertThat(result.at("/statusEffects/contractsEnded").asInt()).isEqualTo(1);
        assertThat(result.at("/linkedUser/active").asBoolean()).isFalse();

        // Their sessions end and they cannot sign in again.
        assertThat(userRepository.findById(cashier.getId()).orElseThrow().isActive()).isFalse();
        mockMvc.perform(get("/hr/me/profile").header("Authorization", tokenBefore)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", cashier.getPhone(), "password", "secret123"))))
                .andExpect(status().isForbidden());

        // The shift is now an open shift at the branch; the contract ended.
        JsonNode board = json(getJson("/hr/schedules?branchId=" + accra.getId() + "&from=" + later + "&to=" + later, manager));
        boolean open = false;
        for (JsonNode s : board.get("shifts")) {
            if (s.get("id").asText().equals(shiftId)) open = s.get("employeeId").isNull();
        }
        assertThat(open).isTrue();
        getJson("/hr/employees/" + employee + "/contracts", admin).andExpect(jsonPath("$[0].status").value("ENDED"));

        // Coming back switches the login on again (HR switched it off).
        JsonNode back = json(postJson("/hr/employees/" + employee + "/status",
                Map.of("status", "ACTIVE", "reason", "Rehired"), admin).andExpect(status().isOk()));
        assertThat(back.at("/statusEffects/loginRestored").asBoolean()).isTrue();
        assertThat(userRepository.findById(cashier.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void suspension_canKeepTheLogin() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "SUSPENDED");
        body.put("reason", "Investigation");
        body.put("keepLoginActive", true);
        postJson("/hr/employees/" + employee + "/status", body, admin).andExpect(status().isOk());
        assertThat(userRepository.findById(cashier.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void idNumbers_areValidated_unique_andOnlyShownToPayStaff() throws Exception {
        Map<String, Object> body = employeeBody("Kofi", "Mensah", accra);
        body.put("ssnitNumber", "bad");
        postJson("/hr/employees", body, admin).andExpect(status().isBadRequest());
        body.put("ssnitNumber", "c123456789012");
        body.put("nationalId", "GHA-123456789-0");
        JsonNode created = json(postJson("/hr/employees", body, admin).andExpect(status().isCreated()));
        assertThat(created.at("/statutory/ssnitNumber").asText()).isEqualTo("C123456789012");

        Map<String, Object> twin = employeeBody("Yaw", "Twin", accra);
        twin.put("ssnitNumber", "C123456789012");
        postJson("/hr/employees", twin, admin).andExpect(status().isConflict());

        // A branch manager (no pay permissions by default) sees no ID numbers.
        getJson("/hr/employees/" + created.at("/summary/id").asText(), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutory").doesNotExist());
    }

    @Test
    void directory_flagsMissingInformation_andExportsCsv() throws Exception {
        JsonNode list = json(getJson("/hr/employees?branchId=" + accra.getId(), admin).andExpect(status().isOk()));
        JsonNode attention = list.get("items").get(0).get("attention");
        assertThat(attention.toString()).contains("NO_CONTRACT").contains("NO_PAYOUT").contains("NO_SSNIT");

        String csv = mockMvc.perform(get("/hr/employees/export?branchId=" + accra.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"Employee number\"").contains("\"Ama Cashier\"").contains("SSNIT number");
        String managerCsv = mockMvc.perform(get("/hr/employees/export?branchId=" + accra.getId()).header("Authorization", bearer(manager)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(managerCsv).doesNotContain("SSNIT number");
    }

    @Test
    void createLogin_fromTheEmployee_withRoleLimits() throws Exception {
        UserEntity hr = staff("HR Officer", Role.HR, null);
        grant(hr, HrPermission.values());
        Map<String, Object> body = employeeBody("Esi", "Rider", accra);
        body.put("phone", "+233241112233");
        String id = json(postJson("/hr/employees", body, admin).andExpect(status().isCreated())).at("/summary/id").asText();

        postJson("/hr/employees/" + id + "/create-login", Map.of("role", "MANAGER", "temporaryPassword", "welcome123"), hr)
                .andExpect(status().isForbidden());
        postJson("/hr/employees/" + id + "/create-login", Map.of("role", "RIDER", "temporaryPassword", "welcome123"), hr)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedUser.role").value("RIDER"));
        postJson("/hr/employees/" + id + "/create-login", Map.of("role", "RIDER", "temporaryPassword", "welcome123"), hr)
                .andExpect(status().isConflict());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "+233241112233", "password", "welcome123"))))
                .andExpect(status().isOk());
    }
}
