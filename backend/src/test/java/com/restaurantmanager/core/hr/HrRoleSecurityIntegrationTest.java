package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HR role works only in the HR module, and HR endpoints refuse everyone without the right permission. */
class HrRoleSecurityIntegrationTest extends HrIntegrationTestSupport {
    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity centralHr;
    private UserEntity accraHr;
    private UserEntity rider;
    private UserEntity customer;

    @BeforeEach
    void setUpData() {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        centralHr = staff("Central HR", Role.HR, null);
        accraHr = staff("Accra HR", Role.HR, accra);
        rider = staff("Rider", Role.RIDER, accra);
        customer = createUser("Customer", "+233249990001", "customer-hr@x.com", "secret123", Role.CUSTOMER);
    }

    @Test
    void hrRole_hasTheWholeHrModule_centralSeesEveryBranch_branchHrOnlyTheirs() throws Exception {
        UUID inAccra = createEmployee(admin, "Ama", "Accra", accra);
        UUID inKumasi = createEmployee(admin, "Kofi", "Kumasi", kumasi);

        JsonNode access = json(getJson("/hr/me/access", centralHr).andExpect(status().isOk()));
        assertThat(access.get("allBranches").asBoolean()).isTrue();
        assertThat(access.get("permissions").toString()).contains("hr.payroll.approve", "hr.compensation.view", "hr.reports.view")
                .doesNotContain("hr.branch.all");
        assertThat(json(getJson("/hr/me/access", accraHr)).get("allBranches").asBoolean()).isFalse();

        getJson("/hr/employees/" + inAccra, centralHr).andExpect(status().isOk());
        getJson("/hr/employees/" + inKumasi, centralHr).andExpect(status().isOk());
        getJson("/hr/employees/" + inAccra, accraHr).andExpect(status().isOk());
        getJson("/hr/employees/" + inKumasi, accraHr).andExpect(status().isNotFound()); // branch isolation
        getJson("/hr/dashboard", centralHr).andExpect(status().isOk());
        getJson("/hr/dashboard?branchId=" + kumasi.getId(), accraHr).andExpect(status().isForbidden());

        // the lookups HR screens need
        getJson("/branches", centralHr).andExpect(status().isOk());
        getJson("/scheduling/staff", centralHr).andExpect(status().isOk());
        getJson("/users/me", centralHr).andExpect(status().isOk());

        // HR does not hand out HR permissions: that stays with the owner
        putJson("/hr/access/grants/" + rider.getId(), Map.of("permissions", List.of("hr.employee.view")), centralHr).andExpect(status().isForbidden());
    }

    @Test
    void hrRole_isRefusedEverythingOutsideHr_whileOtherRolesKeepTheirAccess() throws Exception {
        for (String path : List.of("/inventory/ingredients", "/tables", "/kds/board", "/shifts/active", "/users/me/favourites")) {
            getJson(path, centralHr).andExpect(status().isForbidden());
        }
        postJson("/orders", Map.of("type", "PICKUP", "items", List.of()), centralHr).andExpect(status().isForbidden());

        getJson("/inventory/ingredients", manager).andExpect(status().isOk());
        getJson("/tables", manager).andExpect(status().isOk());
        getJson("/users/me/favourites", customer).andExpect(status().isOk()); // open to any signed-in non-HR account
    }

    @Test
    void assigningTheHrRoleWithoutABranch_makesCentralHr() throws Exception {
        UserEntity cashier = staff("Cashier", Role.CASHIER, accra);
        mockMvc.perform(patch("/users/" + cashier.getId() + "/role").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("role", "HR"))))
                .andExpect(status().isOk());
        UserEntity updated = userRepository.findById(cashier.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.HR);
        assertThat(updated.getBranch()).isNull();
        assertThat(json(getJson("/hr/me/access", updated)).get("allBranches").asBoolean()).isTrue();
        getJson("/inventory/ingredients", updated).andExpect(status().isForbidden());
    }

    @Test
    void hrEndpoints_needSignInAndThePermission() throws Exception {
        List<String> protectedReads = List.of("/hr/employees", "/hr/dashboard", "/hr/payroll-runs", "/hr/tip-pools", "/hr/vacancies",
                "/hr/candidates", "/hr/access/grants", "/hr/contracts/due");
        for (String path : protectedReads) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            getJson(path, customer).andExpect(status().isForbidden());
            getJson(path, rider).andExpect(status().isForbidden()); // staff, but no HR permission
        }
        // self-service needs a linked employee record, not a permission
        getJson("/hr/me/profile", rider).andExpect(status().isNotFound());
        getJson("/hr/me/contracts", rider).andExpect(status().isNotFound());
        getJson("/hr/me/documents", rider).andExpect(status().isNotFound());
        getJson("/hr/me/profile", customer).andExpect(status().isForbidden());
        mockMvc.perform(post("/hr/employees")).andExpect(status().isUnauthorized());
    }
}
