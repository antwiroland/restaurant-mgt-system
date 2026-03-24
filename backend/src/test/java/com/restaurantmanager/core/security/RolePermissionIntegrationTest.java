package com.restaurantmanager.core.security;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RolePermissionIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenAdminToken_whenAccessAdminOnlyEndpoint_then200() throws Exception {
        UserEntity admin = createUser("Admin", "+233200000001", "admin@x.com", "secret123", Role.ADMIN);
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void givenCashierToken_whenAccessAdminOnlyEndpoint_then403() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233200000002", "cashier@x.com", "secret123", Role.CASHIER);
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenManagerToken_whenAccessAdminOnlyEndpoint_then403() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenCustomerToken_whenAccessStaffEndpoint_then403() throws Exception {
        UserEntity customer = createUser("Customer", "+233200000004", "customer@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenAdminToken_whenAssignRole_then200AndRoleUpdated() throws Exception {
        UserEntity admin = createUser("Admin", "+233200000001", "admin@x.com", "secret123", Role.ADMIN);
        UserEntity target = createUser("Target", "+233200000005", "target@x.com", "secret123", Role.CUSTOMER);

        mockMvc.perform(patch("/users/" + target.getId() + "/role")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    @Test
    void givenManagerToken_whenAssignRole_then403() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        UserEntity target = createUser("Target", "+233200000005", "target@x.com", "secret123", Role.CUSTOMER);

        mockMvc.perform(patch("/users/" + target.getId() + "/role")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isForbidden());
    }
}
