package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-service: own contact details, own contracts and own documents. */
class HrSelfServiceIntegrationTest extends HrIntegrationTestSupport {
    private static final byte[] PDF = "%PDF-1.4\n%self\n".getBytes(StandardCharsets.US_ASCII);

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity kofi;
    private UUID kofiEmployee;
    private UUID colleague;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        admin = staff("Owner", Role.ADMIN, null);
        kofi = staff("Kofi", Role.CASHIER, accra);
        Map<String, Object> body = employeeBody("Kofi", "Boateng", accra);
        body.put("userId", kofi.getId());
        body.put("phone", "+233201000001");
        kofiEmployee = UUID.fromString(json(postJson("/hr/employees", body, admin).andExpect(status().isCreated())).at("/summary/id").asText());
        Map<String, Object> other = employeeBody("Abena", "Asante", accra);
        other.put("phone", "+233201000002");
        colleague = UUID.fromString(json(postJson("/hr/employees", other, admin).andExpect(status().isCreated())).at("/summary/id").asText());
    }

    @Test
    void employeeUpdatesOwnContactDetails_butNothingElse() throws Exception {
        Map<String, Object> mine = Map.of("phone", "+233209999999", "email", "Kofi.New@Example.test", "address", "12 Ring Road, Accra",
                "emergencyContacts", List.of(Map.of("name", "Ama Boateng", "relationship", "Sister", "phone", "+233201111111")));
        JsonNode updated = json(putJson("/hr/me/profile", mine, kofi).andExpect(status().isOk()));
        assertThat(updated.at("/summary/phone").asText()).isEqualTo("+233209999999");
        assertThat(updated.at("/summary/email").asText()).isEqualTo("kofi.new@example.test");
        assertThat(updated.get("emergencyContacts")).hasSize(1);
        assertThat(updated.at("/emergencyContacts/0/name").asText()).isEqualTo("Ama Boateng");

        JsonNode asHr = json(getJson("/hr/employees/" + kofiEmployee, admin));
        assertThat(asHr.get("address").asText()).isEqualTo("12 Ring Road, Accra");
        assertThat(asHr.at("/summary/employmentType").asText()).isEqualTo("FULL_TIME"); // employment data untouched
        assertThat(asHr.at("/summary/primaryBranchId").asText()).isEqualTo(accra.getId().toString());

        putJson("/hr/me/profile", Map.of("phone", "+233201000002"), kofi).andExpect(status().isConflict()); // the colleague's number
        putJson("/hr/me/profile", Map.of("phone", "not a phone"), kofi).andExpect(status().isBadRequest());
        putJson("/hr/me/profile", Map.of("phone", "+233209999999", "version", 0), kofi).andExpect(status().isConflict()); // stale
    }

    @Test
    void employeeSeesOwnAgreedContractsWithPay_andOwnDocumentsOnly() throws Exception {
        Map<String, Object> contract = Map.of("contractType", "PERMANENT", "startDate", "2025-01-15", "salaryType", "MONTHLY",
                "salaryAmount", 2500, "weeklyHours", 40);
        String active = json(postJson("/hr/employees/" + kofiEmployee + "/contracts", contract, admin).andExpect(status().isCreated())).get("id").asText();
        postJson("/hr/contracts/" + active + "/activate", Map.of(), admin).andExpect(status().isOk());
        Map<String, Object> draft = Map.of("contractType", "PERMANENT", "startDate", "2026-01-01", "salaryType", "MONTHLY", "salaryAmount", 2700);
        postJson("/hr/employees/" + kofiEmployee + "/contracts", draft, admin).andExpect(status().isCreated()); // not agreed yet

        JsonNode contracts = json(getJson("/hr/me/contracts", kofi).andExpect(status().isOk()));
        assertThat(contracts).hasSize(1);
        assertThat(contracts.get(0).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(contracts.get(0).get("salaryAmount").decimalValue()).isEqualByComparingTo("2500");

        String own = upload(kofiEmployee, "Food safety certificate");
        String theirs = upload(colleague, "Colleague ID");
        JsonNode docs = json(getJson("/hr/me/documents", kofi).andExpect(status().isOk()));
        assertThat(docs.findValuesAsText("id")).containsExactly(own);
        byte[] file = getJson("/hr/me/documents/" + own + "/download", kofi).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(file).isEqualTo(PDF);
        getJson("/hr/me/documents/" + theirs + "/download", kofi).andExpect(status().isNotFound());
        getJson("/hr/documents/" + own + "/download", kofi).andExpect(status().isForbidden()); // the HR route needs hr.document.view
    }

    private String upload(UUID employeeId, String title) throws Exception {
        return json(mockMvc.perform(multipart("/hr/employees/" + employeeId + "/documents")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF))
                        .param("title", title).param("documentType", "OTHER").header("Authorization", bearer(admin)))
                .andExpect(status().isCreated())).get("id").asText();
    }
}
