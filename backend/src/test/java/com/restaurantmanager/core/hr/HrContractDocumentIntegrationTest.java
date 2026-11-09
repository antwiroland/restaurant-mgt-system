package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.contract.ContractRepository;
import com.restaurantmanager.core.hr.contract.ContractService;
import com.restaurantmanager.core.hr.contract.ContractStatus;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrContractDocumentIntegrationTest extends HrIntegrationTestSupport {
    private static final byte[] PDF = "%PDF-1.4\n%test contract\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private ContractService contractService;
    @Autowired
    private EmployeeRepository employeeRepository;

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity accraManager;
    private UUID employeeId;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        accraManager = staff("Accra Manager", Role.MANAGER, accra);
        employeeId = createEmployee(admin, "Ama", "Mensah", accra);
    }

    private Map<String, Object> contractBody(String start, String end, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("contractType", "FIXED_TERM");
        body.put("startDate", start);
        body.put("endDate", end);
        body.put("salaryType", amount == null ? null : "MONTHLY");
        body.put("salaryAmount", amount);
        body.put("weeklyHours", 40);
        return body;
    }

    private ResultActions upload(UserEntity user, UUID employee, byte[] bytes, String name) throws Exception {
        return mockMvc.perform(multipart("/hr/employees/" + employee + "/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .param("title", "Signed contract")
                .param("documentType", "EMPLOYMENT_CONTRACT")
                .header("Authorization", bearer(user)));
    }

    @Test
    void salaryIsMaskedWithoutCompensationPermission() throws Exception {
        postJson("/hr/employees/" + employeeId + "/contracts", contractBody("2025-01-01", "2026-12-31", "2500.00"), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractNumber").value("CTR-000001"))
                .andExpect(jsonPath("$.salaryAmount").value(2500.00))
                .andExpect(jsonPath("$.currency").value("GHS"));

        getJson("/hr/employees/" + employeeId + "/contracts", accraManager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payVisible").value(false))
                .andExpect(jsonPath("$[0].salaryAmount").doesNotExist());

        assertThat(auditLogRepository.findByAction(AuditAction.HR_CONTRACT_CREATED))
                .allSatisfy(a -> assertThat(a.getMetadata()).doesNotContain("2500"));
    }

    @Test
    void contractManagerWithoutCompensation_cannotSetPay() throws Exception {
        UserEntity hrOfficer = staff("HR Officer", Role.CASHIER, accra);
        grant(hrOfficer, HrPermission.CONTRACT_MANAGE, HrPermission.CONTRACT_VIEW);
        postJson("/hr/employees/" + employeeId + "/contracts", contractBody("2025-01-01", "2026-12-31", "2500.00"), hrOfficer)
                .andExpect(status().isForbidden());
        postJson("/hr/employees/" + employeeId + "/contracts", contractBody("2025-01-01", "2026-12-31", null), hrOfficer)
                .andExpect(status().isCreated());
    }

    @Test
    void lifecycle_activateRenewEnd_keepsOneActiveContract() throws Exception {
        String first = json(postJson("/hr/employees/" + employeeId + "/contracts", contractBody("2025-01-01", "2030-12-31", "2000"), admin)
                .andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + first + "/activate", Map.of(), admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        postJson("/hr/contracts/" + first + "/activate", Map.of(), admin).andExpect(status().isConflict());
        putJson("/hr/contracts/" + first, contractBody("2025-01-01", "2031-12-31", "2000"), admin).andExpect(status().isConflict());

        String other = json(postJson("/hr/employees/" + employeeId + "/contracts", contractBody("2026-01-01", "2027-12-31", "2100"), admin)
                .andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + other + "/activate", Map.of(), admin).andExpect(status().isConflict());

        String renewal = json(postJson("/hr/contracts/" + first + "/renew", contractBody("2031-01-01", "2032-12-31", "2400"), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.previousContractId").value(first))).get("id").asText();
        postJson("/hr/contracts/" + first + "/renew", contractBody("2031-01-01", "2032-12-31", "2400"), admin).andExpect(status().isConflict());
        postJson("/hr/contracts/" + renewal + "/activate", Map.of(), admin).andExpect(status().isOk());

        assertThat(contractRepository.findById(UUID.fromString(first)).orElseThrow().getStatus()).isEqualTo(ContractStatus.RENEWED);
        postJson("/hr/contracts/" + renewal + "/end", Map.of("reason", "Mutual agreement", "endDate", "2031-06-30"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED")).andExpect(jsonPath("$.endDate").value("2031-06-30"));
        // ending a contract never changes the employee's status
        assertThat(employeeRepository.findById(employeeId).orElseThrow().getEmploymentStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void fixedTermNeedsEndDate_andProbationMustFitThePeriod() throws Exception {
        postJson("/hr/employees/" + employeeId + "/contracts", contractBody("2025-01-01", null, null), admin).andExpect(status().isBadRequest());
        Map<String, Object> probation = contractBody("2025-01-01", "2025-12-31", null);
        probation.put("probationEndDate", "2026-03-01");
        postJson("/hr/employees/" + employeeId + "/contracts", probation, admin).andExpect(status().isBadRequest());
    }

    @Test
    void expiryJobMarksPastContractsExpired_withoutTouchingTheEmployee() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Accra"));
        String id = json(postJson("/hr/employees/" + employeeId + "/contracts",
                contractBody(today.minusYears(1).toString(), today.plusDays(10).toString(), null), admin)).get("id").asText();
        postJson("/hr/contracts/" + id + "/activate", Map.of(), admin).andExpect(status().isOk());

        getJson("/hr/contracts/due?days=30", accraManager).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeName").value("Ama Mensah"));
        UserEntity kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        getJson("/hr/contracts/due?days=30", kumasiManager).andExpect(jsonPath("$.length()").value(0));

        var contract = contractRepository.findById(UUID.fromString(id)).orElseThrow();
        contract.setEndDate(today.minusDays(1));
        contractRepository.save(contract);
        assertThat(contractService.expireEndedContracts()).isEqualTo(1);
        assertThat(contractRepository.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(ContractStatus.EXPIRED);
        assertThat(employeeRepository.findById(employeeId).orElseThrow().getEmploymentStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void documents_uploadValidatedDownloadProtectedAndAudited() throws Exception {
        JsonNode doc = json(upload(admin, employeeId, PDF, "../../etc/contract<1>.pdf").andExpect(status().isCreated()));
        assertThat(doc.get("fileName").asText()).isEqualTo("contract_1_.pdf");
        assertThat(doc.get("contentType").asText()).isEqualTo("application/pdf");
        String docId = doc.get("id").asText();

        // executable disguised as pdf, empty file
        upload(admin, employeeId, new byte[]{'M', 'Z', 0, 0, 1}, "cv.pdf").andExpect(status().isBadRequest());
        upload(admin, employeeId, "PK\u0003\u0004zip".getBytes(StandardCharsets.ISO_8859_1), "archive.zip").andExpect(status().isBadRequest());
        // managers can view but not upload by default
        upload(accraManager, employeeId, PDF, "x.pdf").andExpect(status().isForbidden());

        mockMvc.perform(get("/hr/documents/" + docId + "/download").header("Authorization", bearer(accraManager)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(PDF));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_DOCUMENT_DOWNLOADED)).hasSize(1);

        UserEntity kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        UserEntity cashier = staff("Cashier", Role.CASHIER, accra);
        mockMvc.perform(get("/hr/documents/" + docId + "/download").header("Authorization", bearer(kumasiManager)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/hr/documents/" + docId + "/download").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/media/" + docId)).andExpect(status().isNotFound());

        postJson("/hr/documents/" + docId + "/archive", Map.of(), admin).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        getJson("/hr/employees/" + employeeId + "/documents", admin).andExpect(jsonPath("$.length()").value(0));
        getJson("/hr/employees/" + employeeId + "/documents?includeArchived=true", admin).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void contractCanReferenceOnlyTheSameEmployeesDocument() throws Exception {
        UUID other = createEmployee(admin, "Kofi", "Other", accra);
        String otherDoc = json(upload(admin, other, PDF, "c.pdf").andExpect(status().isCreated())).get("id").asText();
        Map<String, Object> body = contractBody("2025-01-01", "2026-01-01", null);
        body.put("documentId", otherDoc);
        postJson("/hr/employees/" + employeeId + "/contracts", body, admin).andExpect(status().isBadRequest());
    }
}
