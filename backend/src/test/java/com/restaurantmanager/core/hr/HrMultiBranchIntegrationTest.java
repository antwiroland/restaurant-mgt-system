package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.branch.AssignmentType;
import com.restaurantmanager.core.hr.branch.BranchAssignmentEntity;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrMultiBranchIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    @Autowired
    private BranchAssignmentRepository assignmentRepository;

    private BranchEntity accra;
    private BranchEntity kumasi;
    private BranchEntity tamale;
    private UserEntity admin;
    private UserEntity accraManager;
    private UserEntity kumasiManager;
    private LocalDate today;

    @BeforeEach
    void setUpData() {
        accra = branch("ACC");
        kumasi = branch("KSI");
        tamale = branch("TML");
        admin = staff("Owner", Role.ADMIN, null);
        accraManager = staff("Accra Manager", Role.MANAGER, accra);
        kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        today = LocalDate.now(ZONE);
    }

    private List<BranchAssignmentEntity> history(UUID employeeId) {
        return assignmentRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId);
    }

    @Test
    void newEmployee_getsPrimaryAssignmentFromHireDate() throws Exception {
        UUID id = createEmployee(admin, "Ama", "Mensah", accra);
        List<BranchAssignmentEntity> rows = history(id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAssignmentType()).isEqualTo(AssignmentType.PRIMARY);
        assertThat(rows.get(0).getBranchId()).isEqualTo(accra.getId());
        assertThat(rows.get(0).getStartDate()).isEqualTo(LocalDate.parse("2025-01-15"));
        assertThat(rows.get(0).getEndDate()).isNull();
    }

    @Test
    void transfer_closesOldPrimaryOpensNew_movesVisibility_andIsAudited() throws Exception {
        UUID id = createEmployee(admin, "Ama", "Mover", accra);
        LocalDate effective = today.minusDays(3);

        postJson("/hr/employees/" + id + "/transfer", Map.of("toBranchId", kumasi.getId(), "effectiveDate", effective.toString()), accraManager)
                .andExpect(status().isForbidden());
        postJson("/hr/employees/" + id + "/transfer", Map.of("toBranchId", kumasi.getId(), "effectiveDate", today.plusDays(1).toString()), admin)
                .andExpect(status().isBadRequest());
        postJson("/hr/employees/" + id + "/transfer", Map.of("toBranchId", kumasi.getId(), "effectiveDate", "2024-01-01"), admin)
                .andExpect(status().isBadRequest());
        postJson("/hr/employees/" + id + "/transfer", Map.of("toBranchId", accra.getId()), admin).andExpect(status().isBadRequest());

        postJson("/hr/employees/" + id + "/transfer",
                Map.of("toBranchId", kumasi.getId(), "effectiveDate", effective.toString(), "reason", "New branch opening"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.primaryBranchId").value(kumasi.getId().toString()));

        List<BranchAssignmentEntity> rows = history(id);
        assertThat(rows).hasSize(2);
        BranchAssignmentEntity current = rows.stream().filter(a -> a.getEndDate() == null).findFirst().orElseThrow();
        BranchAssignmentEntity previous = rows.stream().filter(a -> a.getEndDate() != null).findFirst().orElseThrow();
        assertThat(current.getBranchId()).isEqualTo(kumasi.getId());
        assertThat(current.getStartDate()).isEqualTo(effective);
        assertThat(previous.getBranchId()).isEqualTo(accra.getId());
        assertThat(previous.getEndDate()).isEqualTo(effective.minusDays(1));

        getJson("/hr/employees/" + id, accraManager).andExpect(status().isNotFound());
        getJson("/hr/employees/" + id, kumasiManager).andExpect(status().isOk()).andExpect(jsonPath("$.manageable").value(true));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_EMPLOYEE_TRANSFERRED))
                .anyMatch(a -> id.equals(a.getEntityId()) && a.getMetadata().contains("New branch opening"));
        getJson("/hr/employees/" + id + "/assignments", kumasiManager).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].branchName").value("Branch KSI"))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void sameDayBranchChange_correctsTheOpenAssignmentInsteadOfAddingOne() throws Exception {
        Map<String, Object> body = new HashMap<>(employeeBody("Kofi", "Typo", accra));
        body.put("hireDate", today.toString());
        UUID id = UUID.fromString(json(postJson("/hr/employees", body, admin).andExpect(status().isCreated())).at("/summary/id").asText());
        body.put("primaryBranchId", kumasi.getId());
        putJson("/hr/employees/" + id, body, admin).andExpect(status().isOk());
        assertThat(history(id)).singleElement().satisfies(a -> {
            assertThat(a.getBranchId()).isEqualTo(kumasi.getId());
            assertThat(a.getEndDate()).isNull();
        });
    }

    @Test
    void secondaryAssignment_letsTheCoveringBranchSeeButNotManage() throws Exception {
        UUID id = createEmployee(admin, "Esi", "Floater", accra);
        getJson("/hr/employees/" + id, kumasiManager).andExpect(status().isNotFound());

        // managers do not hold hr.branch.assign by default
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", kumasi.getId(), "assignmentType", "SECONDARY"), accraManager)
                .andExpect(status().isForbidden());

        JsonNode assignment = json(postJson("/hr/employees/" + id + "/assignments",
                Map.of("branchId", kumasi.getId(), "assignmentType", "SECONDARY", "startDate", today.minusDays(1).toString(), "reason", "Weekend cover"), admin)
                .andExpect(status().isCreated()));

        getJson("/hr/employees/" + id, kumasiManager).andExpect(status().isOk()).andExpect(jsonPath("$.manageable").value(false));
        getJson("/hr/employees?q=floater", kumasiManager).andExpect(jsonPath("$.total").value(1));
        getJson("/hr/employees?branchId=" + kumasi.getId(), admin).andExpect(jsonPath("$.total").value(1));
        getJson("/hr/employees/" + id + "/contracts", kumasiManager).andExpect(status().isNotFound());
        getJson("/hr/employees/" + id + "/documents", kumasiManager).andExpect(status().isNotFound());
        getJson("/hr/employees/" + id + "/audit", kumasiManager).andExpect(status().isNotFound());
        putJson("/hr/employees/" + id, employeeBody("Esi", "Changed", accra), kumasiManager).andExpect(status().isNotFound());
        getJson("/hr/employees/" + id, staff("Tamale Manager", Role.MANAGER, tamale)).andExpect(status().isNotFound());

        // overlapping second assignment at the same branch, and the primary branch itself, are rejected
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", kumasi.getId(), "assignmentType", "SECONDARY"), admin)
                .andExpect(status().isConflict());
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", accra.getId(), "assignmentType", "SECONDARY"), admin)
                .andExpect(status().isBadRequest());
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", tamale.getId(), "assignmentType", "PRIMARY"), admin)
                .andExpect(status().isBadRequest());

        postJson("/hr/assignments/" + assignment.get("id").asText() + "/end",
                Map.of("endDate", today.minusDays(1).toString(), "reason", "Cover finished"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.current").value(false));
        getJson("/hr/employees/" + id, kumasiManager).andExpect(status().isNotFound());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_BRANCH_ASSIGNMENT_CHANGED)).hasSize(2);
    }

    @Test
    void temporaryAssignment_needsEndDate_andExpiresOnItsOwn() throws Exception {
        UUID id = createEmployee(admin, "Yaw", "Temp", accra);
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", tamale.getId(), "assignmentType", "TEMPORARY"), admin)
                .andExpect(status().isBadRequest());
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", tamale.getId(), "assignmentType", "TEMPORARY",
                "startDate", today.minusDays(10).toString(), "endDate", today.minusDays(2).toString()), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.current").value(false));
        UserEntity tamaleManager = staff("Tamale Manager", Role.MANAGER, tamale);
        getJson("/hr/employees/" + id, tamaleManager).andExpect(status().isNotFound());

        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", tamale.getId(), "assignmentType", "TEMPORARY",
                "startDate", today.toString(), "endDate", today.plusDays(5).toString()), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.current").value(true));
        getJson("/hr/employees/" + id, tamaleManager).andExpect(status().isOk());
    }

    @Test
    void exitClosesAllAssignments_andRehireOpensANewPrimary() throws Exception {
        UUID id = createEmployee(admin, "Abena", "Leaver", accra);
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", kumasi.getId(), "assignmentType", "SECONDARY",
                "startDate", "2025-02-01"), admin).andExpect(status().isCreated());
        LocalDate lastDay = today.minusDays(1);
        postJson("/hr/employees/" + id + "/status", Map.of("status", "RESIGNED", "reason", "Studies", "effectiveDate", lastDay.toString()), admin)
                .andExpect(status().isOk());
        assertThat(history(id)).allSatisfy(a -> assertThat(a.getEndDate()).isEqualTo(lastDay));
        postJson("/hr/employees/" + id + "/transfer", Map.of("toBranchId", kumasi.getId()), admin).andExpect(status().isBadRequest());

        postJson("/hr/employees/" + id + "/status", Map.of("status", "ACTIVE", "reason", "Back after studies"), admin).andExpect(status().isOk());
        assertThat(history(id)).filteredOn(a -> a.getEndDate() == null).singleElement().satisfies(a -> {
            assertThat(a.getAssignmentType()).isEqualTo(AssignmentType.PRIMARY);
            assertThat(a.getStartDate()).isEqualTo(today);
        });
    }

    @Test
    void extraBranchScope_extendsAManagerToASecondBranchOnly() throws Exception {
        createEmployee(admin, "Ama", "Accra", accra);
        createEmployee(admin, "Kofi", "Kumasi", kumasi);
        createEmployee(admin, "Yaw", "Tamale", tamale);
        getJson("/hr/employees", accraManager).andExpect(jsonPath("$.total").value(1));

        putJson("/hr/access/branches/" + accraManager.getId(), Map.of("branchIds", List.of(kumasi.getId())), accraManager)
                .andExpect(status().isForbidden());
        putJson("/hr/access/branches/" + admin.getId(), Map.of("branchIds", List.of(kumasi.getId())), admin)
                .andExpect(status().isBadRequest());
        putJson("/hr/access/branches/" + accraManager.getId(), Map.of("branchIds", List.of(UUID.randomUUID())), admin)
                .andExpect(status().isNotFound());
        putJson("/hr/access/branches/" + accraManager.getId(), Map.of("branchIds", List.of(kumasi.getId(), accra.getId())), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraBranchIds.length()").value(1));

        getJson("/hr/employees", accraManager).andExpect(jsonPath("$.total").value(2));
        getJson("/hr/me/access", accraManager).andExpect(jsonPath("$.branchIds.length()").value(2)).andExpect(jsonPath("$.allBranches").value(false));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_BRANCH_ACCESS_CHANGED)).hasSize(1);

        putJson("/hr/access/branches/" + accraManager.getId(), Map.of("branchIds", List.of()), admin).andExpect(status().isOk());
        getJson("/hr/employees", accraManager).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void workforceSummary_countsPerVisibleBranch() throws Exception {
        createEmployee(admin, "A", "One", accra);
        createEmployee(admin, "B", "Two", accra);
        UUID leaver = createEmployee(admin, "C", "Three", accra);
        postJson("/hr/employees/" + leaver + "/status", Map.of("status", "RETIRED", "reason", "Retirement"), admin).andExpect(status().isOk());
        UUID onLeave = createEmployee(admin, "D", "Four", kumasi);
        postJson("/hr/employees/" + onLeave + "/status", Map.of("status", "ON_LEAVE"), admin).andExpect(status().isOk());
        UUID floater = createEmployee(admin, "E", "Five", kumasi);
        postJson("/hr/employees/" + floater + "/assignments", Map.of("branchId", accra.getId(), "assignmentType", "SECONDARY"), admin)
                .andExpect(status().isCreated());
        createEmployee(admin, "F", "HeadOffice", null);

        JsonNode all = json(getJson("/hr/branches/workforce", admin).andExpect(status().isOk()));
        Map<String, JsonNode> byName = new HashMap<>();
        all.forEach(n -> byName.put(n.get("name").asText(), n));
        assertThat(byName.get("Branch ACC").get("currentEmployees").asLong()).isEqualTo(2);
        assertThat(byName.get("Branch ACC").get("coveringFromOtherBranches").asLong()).isEqualTo(1);
        assertThat(byName.get("Branch KSI").get("currentEmployees").asLong()).isEqualTo(2);
        assertThat(byName.get("Branch KSI").get("onLeave").asLong()).isEqualTo(1);
        assertThat(byName.get("No branch (head office)").get("currentEmployees").asLong()).isEqualTo(1);

        getJson("/hr/branches/workforce", accraManager).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Branch ACC"));
        getJson("/hr/branches/workforce", staff("Cashier", Role.CASHIER, accra)).andExpect(status().isForbidden());
    }

    @Test
    void centralGrantHolder_canAssignAcrossBranches_butScopedAssignerOnlyWithinScope() throws Exception {
        UserEntity scopedHr = staff("Scoped HR", Role.CASHIER, accra);
        grant(scopedHr, HrPermission.EMPLOYEE_VIEW, HrPermission.BRANCH_ASSIGN);
        UUID id = createEmployee(admin, "Kwame", "Scoped", accra);
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", kumasi.getId(), "assignmentType", "SECONDARY"), scopedHr)
                .andExpect(status().isForbidden());
        grant(scopedHr, HrPermission.BRANCH_ALL);
        postJson("/hr/employees/" + id + "/assignments", Map.of("branchId", kumasi.getId(), "assignmentType", "SECONDARY"), scopedHr)
                .andExpect(status().isCreated());
    }
}
