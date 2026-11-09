package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BasePostgresIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the HR foundation against the real Flyway schema (V27) on PostgreSQL. */
class HrPostgresFlywayIntegrationTest extends BasePostgresIntegrationTest {

    @Test
    void seededOrganisation_employeeContractAndDocument_workOnMigratedSchema() throws Exception {
        UserEntity admin = createUser("Admin", "+233249900001", "hr-admin@example.com", "secret123", Role.ADMIN);
        String token = "Bearer " + accessToken(admin);

        JsonNode departments = objectMapper.readTree(mockMvc.perform(get("/hr/departments").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(departments.size()).isGreaterThanOrEqualTo(7);
        JsonNode positions = objectMapper.readTree(mockMvc.perform(get("/hr/positions").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String waiter = null;
        for (JsonNode p : positions) {
            if ("WAITER".equals(p.get("code").asText())) {
                waiter = p.get("id").asText();
            }
        }
        assertThat(waiter).isNotNull();

        Map<String, Object> body = Map.of("firstName", "Akua", "lastName", "Asante", "employmentType", "PART_TIME",
                "hireDate", "2025-02-01", "positionId", waiter, "email", "akua@example.com", "phone", "+233201110000",
                "emergencyContacts", List.of(Map.of("name", "Yaa", "phone", "+233201110001")));
        JsonNode employee = objectMapper.readTree(mockMvc.perform(post("/hr/employees").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.departmentName").value("Front of House"))
                .andReturn().getResponse().getContentAsString());
        String id = employee.at("/summary/id").asText();
        assertThat(employee.at("/summary/employeeNumber").asText()).startsWith("EMP-");

        mockMvc.perform(get("/hr/employees").param("q", "asan").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));

        String contract = objectMapper.readTree(mockMvc.perform(post("/hr/employees/" + id + "/contracts").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contractType", "PART_TIME", "startDate", "2025-02-01",
                                "salaryType", "HOURLY", "salaryAmount", "18.50", "weeklyHours", 24))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/hr/contracts/" + contract + "/activate").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(multipart("/hr/employees/" + id + "/documents")
                        .file(new MockMultipartFile("file", "id.pdf", "application/pdf", "%PDF-1.4 id".getBytes(StandardCharsets.US_ASCII)))
                        .param("title", "Ghana Card").param("documentType", "IDENTIFICATION")
                        .header("Authorization", token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/hr/employees/" + id + "/audit").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }
}
