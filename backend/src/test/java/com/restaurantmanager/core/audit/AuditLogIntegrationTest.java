package com.restaurantmanager.core.audit;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.auth.dto.LoginRequest;
import com.restaurantmanager.core.auth.dto.PinVerifyRequest;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void givenSuccessfulLogin_thenUserLoginEventWrittenToAuditLog() throws Exception {
        createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("+233201234567", "secret123"))))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findByAction(AuditAction.USER_LOGIN)).isNotEmpty();
    }

    @Test
    void givenFailedPinAttempt_thenPinFailedEventWrittenToAuditLog() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/pin/verify")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinVerifyRequest("9999", OverrideActionType.DISCOUNT))))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findByAction(AuditAction.PIN_FAILED)).isNotEmpty();
    }

    @Test
    void givenPinLockout_thenPinLockedEventWrittenToAuditLog() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/pin/verify")
                    .header("Authorization", "Bearer " + accessToken(manager))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new PinVerifyRequest("9999", OverrideActionType.DISCOUNT))));
        }

        assertThat(auditLogRepository.findByAction(AuditAction.PIN_LOCKED)).isNotEmpty();
    }

    @Test
    void givenRoleAssigned_thenRoleAssignedEventWrittenToAuditLog() throws Exception {
        UserEntity admin = createUser("Admin", "+233200000001", "admin@x.com", "secret123", Role.ADMIN);
        UserEntity target = createUser("Target", "+233200000005", "target@x.com", "secret123", Role.CUSTOMER);

        mockMvc.perform(patch("/users/" + target.getId() + "/role")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findByAction(AuditAction.ROLE_ASSIGNED)).isNotEmpty();
    }
}
