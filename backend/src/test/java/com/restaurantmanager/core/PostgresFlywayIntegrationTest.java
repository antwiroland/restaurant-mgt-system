package com.restaurantmanager.core;

import com.restaurantmanager.core.auth.dto.LoginRequest;
import com.restaurantmanager.core.auth.dto.RegisterRequest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.dto.CategoryRequest;
import com.restaurantmanager.core.menu.dto.MenuItemRequest;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostgresFlywayIntegrationTest extends BasePostgresIntegrationTest {
    @Test
    void registerAndLoginFlow_runsAgainstPostgresWithFlywaySchema() throws Exception {
        RegisterRequest register = new RegisterRequest("Ama", "+233240000001", "ama@example.com", "secret123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        LoginRequest login = new LoginRequest("+233240000001", "secret123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void menuAndTableEndpoints_useMigratedPostgresTables() throws Exception {
        UserEntity admin = createUser("Admin", "+233240000099", "admin@example.com", "secret123", Role.ADMIN);
        UserEntity manager = createUser("Manager", "+233240000002", "manager@example.com", "secret123", Role.MANAGER);

        String categoryBody = mockMvc.perform(post("/menu/categories")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Rice", "Main dishes", 1, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rice"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID categoryId = UUID.fromString(objectMapper.readTree(categoryBody).get("id").asText());

        mockMvc.perform(post("/menu/items")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuItemRequest(categoryId, "Jollof", "Smoky rice", new java.math.BigDecimal("25.00"), null, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));

        String tableBody = """
                {"number":"T7","capacity":4,"zone":"Patio","branchId":null}
                """;

        String createdTable = mockMvc.perform(post("/tables")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("T7"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tableId = objectMapper.readTree(createdTable).get("id").asText();

        mockMvc.perform(patch("/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OCCUPIED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));

        mockMvc.perform(get("/tables")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Elements", "1"))
                .andExpect(jsonPath("$[0].number").value("T7"));
    }
}
