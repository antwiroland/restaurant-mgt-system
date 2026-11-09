package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrEmployeeIntegrationTest extends HrIntegrationTestSupport {
    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity accraManager;

    @BeforeEach
    void setUpData() {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        accraManager = staff("Accra Manager", Role.MANAGER, accra);
    }

    @Test
    void adminCreatesEmployee_numberIsGenerated_contactsSaved_andAudited() throws Exception {
        JsonNode created = json(postJson("/hr/employees", employeeBody("Ama", "Mensah", accra), admin).andExpect(status().isCreated()));
        assertThat(created.at("/summary/employeeNumber").asText()).isEqualTo("EMP-000001");
        assertThat(created.at("/summary/fullName").asText()).isEqualTo("Ama Mensah");
        assertThat(created.at("/emergencyContacts/0/relationship").asText()).isEqualTo("Brother");

        JsonNode second = json(postJson("/hr/employees", employeeBody("Kofi", "Boateng", kumasi), admin).andExpect(status().isCreated()));
        assertThat(second.at("/summary/employeeNumber").asText()).isEqualTo("EMP-000002");

        UUID id = UUID.fromString(created.at("/summary/id").asText());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_EMPLOYEE_CREATED))
                .anyMatch(a -> id.equals(a.getEntityId()));
    }

    @Test
    void duplicateEmailOrPhone_isRejected() throws Exception {
        Map<String, Object> first = new HashMap<>(employeeBody("Ama", "Mensah", accra));
        first.put("email", "ama@resto.com");
        first.put("phone", "+233241111111");
        postJson("/hr/employees", first, admin).andExpect(status().isCreated());

        Map<String, Object> sameEmail = new HashMap<>(employeeBody("Akosua", "Mensah", accra));
        sameEmail.put("email", "AMA@resto.com");
        postJson("/hr/employees", sameEmail, admin).andExpect(status().isConflict());

        Map<String, Object> samePhone = new HashMap<>(employeeBody("Akosua", "Mensah", accra));
        samePhone.put("phone", "+233241111111");
        postJson("/hr/employees", samePhone, admin).andExpect(status().isConflict());
    }

    @Test
    void branchManager_seesOnlyOwnBranch_andGets404ForOtherBranchEmployee() throws Exception {
        createEmployee(admin, "Ama", "Accra", accra);
        UUID kumasiEmployee = createEmployee(admin, "Kofi", "Kumasi", kumasi);

        getJson("/hr/employees", accraManager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].fullName").value("Ama Accra"));
        getJson("/hr/employees", admin).andExpect(jsonPath("$.total").value(2));

        getJson("/hr/employees/" + kumasiEmployee, accraManager).andExpect(status().isNotFound());
        getJson("/hr/employees?branchId=" + kumasi.getId(), accraManager).andExpect(status().isForbidden());
        postJson("/hr/employees", employeeBody("Yaw", "Elsewhere", kumasi), accraManager).andExpect(status().isForbidden());
    }

    @Test
    void branchManager_createsInOwnBranchByDefault_butCannotTransfer() throws Exception {
        UUID id = createEmployee(accraManager, "Esi", "Default", null);
        JsonNode detail = json(getJson("/hr/employees/" + id, accraManager).andExpect(status().isOk()));
        assertThat(detail.at("/summary/primaryBranchId").asText()).isEqualTo(accra.getId().toString());

        Map<String, Object> move = new HashMap<>(employeeBody("Esi", "Default", kumasi));
        putJson("/hr/employees/" + id, move, accraManager).andExpect(status().isForbidden());

        putJson("/hr/employees/" + id, move, admin).andExpect(status().isOk());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_EMPLOYEE_UPDATED))
                .anyMatch(a -> a.getMetadata().contains("primaryBranchId") && a.getMetadata().contains(kumasi.getId().toString()));
    }

    @Test
    void managerWithoutBranch_hasNoBranchData_andCannotCreate() throws Exception {
        UserEntity floatingManager = staff("Floating", Role.MANAGER, null);
        createEmployee(admin, "Ama", "Accra", accra);
        getJson("/hr/employees", floatingManager).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        postJson("/hr/employees", employeeBody("Yaw", "Nowhere", null), floatingManager).andExpect(status().isForbidden());
    }

    @Test
    void cashierNeedsAGrant_andCustomerIsAlwaysRejected() throws Exception {
        UserEntity cashier = staff("Cashier", Role.CASHIER, accra);
        UserEntity customer = staff("Customer", Role.CUSTOMER, null);
        createEmployee(admin, "Ama", "Accra", accra);

        getJson("/hr/employees", cashier).andExpect(status().isForbidden());
        getJson("/hr/employees", customer).andExpect(status().isForbidden());

        grant(cashier, HrPermission.EMPLOYEE_VIEW);
        getJson("/hr/employees", cashier).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void centralHrGrant_givesAllBranches() throws Exception {
        UserEntity hrOfficer = staff("HR Officer", Role.CASHIER, accra);
        grant(hrOfficer, HrPermission.EMPLOYEE_VIEW, HrPermission.BRANCH_ALL);
        createEmployee(admin, "Ama", "Accra", accra);
        createEmployee(admin, "Kofi", "Kumasi", kumasi);
        getJson("/hr/employees", hrOfficer).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void searchFilterSortAndPaging_runInTheDatabase() throws Exception {
        for (String last : List.of("Asante", "Boateng", "Darko", "Owusu")) {
            createEmployee(admin, "Test", last, accra);
        }
        getJson("/hr/employees?q=boa", admin).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].fullName").value("Test Boateng"));
        getJson("/hr/employees?size=2&page=1", admin)
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].fullName").value("Test Darko"));
        getJson("/hr/employees?sort=name,desc&size=1", admin).andExpect(jsonPath("$.items[0].fullName").value("Test Owusu"));
        getJson("/hr/employees?q=100%25", admin).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void exitRequiresArchivePermissionAndReason_andHidesFromDefaultList() throws Exception {
        UUID id = createEmployee(admin, "Ama", "Leaving", accra);
        postJson("/hr/employees/" + id + "/status", Map.of("status", "RESIGNED", "reason", "Relocating"), accraManager)
                .andExpect(status().isForbidden());
        postJson("/hr/employees/" + id + "/status", Map.of("status", "RESIGNED"), admin).andExpect(status().isBadRequest());
        postJson("/hr/employees/" + id + "/status", Map.of("status", "RESIGNED", "reason", "Relocating", "effectiveDate", "2025-06-30"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.employmentStatus").value("RESIGNED"))
                .andExpect(jsonPath("$.exitDate").value("2025-06-30"));

        getJson("/hr/employees", admin).andExpect(jsonPath("$.total").value(0));
        getJson("/hr/employees?includeExited=true", admin).andExpect(jsonPath("$.total").value(1));

        postJson("/hr/employees/" + id + "/status", Map.of("status", "SUSPENDED", "reason", "x"), accraManager)
                .andExpect(status().isForbidden());
    }

    @Test
    void staleVersion_isRejected() throws Exception {
        UUID id = createEmployee(admin, "Ama", "Mensah", accra);
        Map<String, Object> body = new HashMap<>(employeeBody("Ama", "Mensah-Owusu", accra));
        body.put("version", 0);
        putJson("/hr/employees/" + id, body, admin).andExpect(status().isOk());
        putJson("/hr/employees/" + id, body, admin).andExpect(status().isConflict());
    }

    @Test
    void reportingLineLoop_isRejected() throws Exception {
        UUID boss = createEmployee(admin, "Big", "Boss", accra);
        Map<String, Object> worker = new HashMap<>(employeeBody("Small", "Worker", accra));
        worker.put("reportingManagerId", boss);
        UUID workerId = UUID.fromString(json(postJson("/hr/employees", worker, admin).andExpect(status().isCreated())).at("/summary/id").asText());

        Map<String, Object> bossUpdate = new HashMap<>(employeeBody("Big", "Boss", accra));
        bossUpdate.put("reportingManagerId", workerId);
        putJson("/hr/employees/" + boss, bossUpdate, admin).andExpect(status().isBadRequest());
    }

    @Test
    void positionMustMatchDepartment_andArchivedPositionsCannotBeAssigned() throws Exception {
        String kitchen = json(postJson("/hr/departments", Map.of("code", "KIT", "name", "Kitchen", "category", "KITCHEN"), admin)
                .andExpect(status().isCreated())).get("id").asText();
        String bar = json(postJson("/hr/departments", Map.of("code", "BAR", "name", "Bar", "category", "BAR"), admin)
                .andExpect(status().isCreated())).get("id").asText();
        String chef = json(postJson("/hr/positions", Map.of("code", "chef", "title", "Chef", "departmentId", kitchen), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("CHEF")).andExpect(jsonPath("$.category").value("KITCHEN")))
                .get("id").asText();

        Map<String, Object> mismatch = new HashMap<>(employeeBody("Kwame", "Chef", accra));
        mismatch.put("departmentId", bar);
        mismatch.put("positionId", chef);
        postJson("/hr/employees", mismatch, admin).andExpect(status().isBadRequest());

        Map<String, Object> inferred = new HashMap<>(employeeBody("Kwame", "Chef", accra));
        inferred.put("positionId", chef);
        postJson("/hr/employees", inferred, admin).andExpect(status().isCreated()).andExpect(jsonPath("$.summary.departmentName").value("Kitchen"));

        postJson("/hr/positions/" + chef + "/archive", Map.of(), admin).andExpect(status().isOk());
        Map<String, Object> archived = new HashMap<>(employeeBody("Abena", "Chef", accra));
        archived.put("positionId", chef);
        postJson("/hr/employees", archived, admin).andExpect(status().isBadRequest());

        getJson("/hr/positions", admin).andExpect(jsonPath("$.length()").value(0));
        getJson("/hr/positions?includeArchived=true", admin).andExpect(jsonPath("$.length()").value(1));
        postJson("/hr/departments", Map.of("code", "kit", "name", "Dup", "category", "KITCHEN"), admin).andExpect(status().isConflict());
        postJson("/hr/departments", Map.of("code", "X1", "name", "X", "category", "KITCHEN"), accraManager).andExpect(status().isForbidden());
    }

    @Test
    void linkingUsers_preventsDuplicatesAndCustomers_andEnablesSelfProfile() throws Exception {
        UserEntity cashier = staff("Efua Cashier", Role.CASHIER, accra);
        UserEntity customer = staff("Some Customer", Role.CUSTOMER, null);
        UUID first = createEmployee(admin, "Efua", "Cashier", accra);
        UUID second = createEmployee(admin, "Other", "Person", accra);

        getJson("/hr/me/profile", cashier).andExpect(status().isNotFound());

        postJson("/hr/employees/" + first + "/link-user", Map.of("userId", customer.getId()), admin).andExpect(status().isBadRequest());
        postJson("/hr/employees/" + first + "/link-user", Map.of("userId", cashier.getId()), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedUser.role").value("CASHIER"));
        postJson("/hr/employees/" + second + "/link-user", Map.of("userId", cashier.getId()), admin).andExpect(status().isConflict());

        getJson("/hr/me/profile", cashier).andExpect(status().isOk()).andExpect(jsonPath("$.summary.id").value(first.toString()));
        getJson("/hr/me/access", cashier).andExpect(jsonPath("$.employeeId").value(first.toString()))
                .andExpect(jsonPath("$.permissions.length()").value(0));

        // a branch manager cannot link a user account from another branch
        UserEntity kumasiCashier = staff("Kumasi Cashier", Role.CASHIER, kumasi);
        postJson("/hr/employees/" + second + "/link-user", Map.of("userId", kumasiCashier.getId()), accraManager).andExpect(status().isNotFound());
    }

    @Test
    void importUsers_createsRecordsForUnlinkedStaff_andSkipsCollisions() throws Exception {
        UserEntity cashier = staff("Abena Serwaa Owusu", Role.CASHIER, accra);
        UserEntity rider = staff("Yaw", Role.RIDER, kumasi);
        staff("A Customer", Role.CUSTOMER, null);
        Map<String, Object> clash = new HashMap<>(employeeBody("Existing", "Phone", accra));
        clash.put("phone", rider.getPhone());
        postJson("/hr/employees", clash, admin).andExpect(status().isCreated());

        JsonNode result = json(postJson("/hr/employees/import-users", Map.of("employmentType", "FULL_TIME", "hireDate", "2024-03-01"), admin)
                .andExpect(status().isOk()));
        // admin + accra manager + cashier are imported; the rider collides on phone; customers are never candidates
        assertThat(result.get("created").asInt()).isEqualTo(3);
        assertThat(result.at("/skipped/0/userId").asText()).isEqualTo(rider.getId().toString());

        JsonNode profile = json(getJson("/hr/me/profile", cashier).andExpect(status().isOk()));
        assertThat(profile.get("firstName").asText()).isEqualTo("Abena");
        assertThat(profile.get("middleName").asText()).isEqualTo("Serwaa");
        assertThat(profile.get("lastName").asText()).isEqualTo("Owusu");
        assertThat(profile.at("/summary/primaryBranchId").asText()).isEqualTo(accra.getId().toString());
    }

    @Test
    void grants_onlyAdminManages_customersAndAdminsRejected_andChangesAudited() throws Exception {
        UserEntity cashier = staff("Accountant", Role.CASHIER, accra);
        UserEntity customer = staff("Customer", Role.CUSTOMER, null);

        putJson("/hr/access/grants/" + cashier.getId(), Map.of("permissions", List.of("hr.payroll.view")), accraManager)
                .andExpect(status().isForbidden());
        putJson("/hr/access/grants/" + customer.getId(), Map.of("permissions", List.of("hr.payroll.view")), admin)
                .andExpect(status().isBadRequest());
        putJson("/hr/access/grants/" + admin.getId(), Map.of("permissions", List.of("hr.payroll.view")), admin)
                .andExpect(status().isBadRequest());
        putJson("/hr/access/grants/" + cashier.getId(), Map.of("permissions", List.of("hr.nope")), admin)
                .andExpect(status().isBadRequest());

        putJson("/hr/access/grants/" + cashier.getId(), Map.of("permissions", List.of("hr.payroll.view", "hr.employee.view")), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedPermissions.length()").value(2));
        JsonNode access = json(getJson("/hr/me/access", cashier));
        assertThat(access.get("permissions").toString()).contains("hr.payroll.view", "hr.employee.view");

        putJson("/hr/access/grants/" + cashier.getId(), Map.of("permissions", Set.of("hr.employee.view")), admin).andExpect(status().isOk());
        assertThat(grantRepository.findByUserId(cashier.getId())).hasSize(1);
        assertThat(auditLogRepository.findByAction(AuditAction.HR_PERMISSIONS_CHANGED)).hasSize(2);
    }

    @Test
    void auditTrail_isScopedAndListsEmployeeEvents() throws Exception {
        UUID id = createEmployee(admin, "Ama", "Audited", kumasi);
        getJson("/hr/employees/" + id + "/audit", admin).andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("HR_EMPLOYEE_CREATED"));
        getJson("/hr/employees/" + id + "/audit", accraManager).andExpect(status().isNotFound());
    }
}
