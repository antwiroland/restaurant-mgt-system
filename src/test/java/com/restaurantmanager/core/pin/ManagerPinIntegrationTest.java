package com.restaurantmanager.core.pin;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.auth.dto.PinVerifyRequest;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManagerPinIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenManagerSetsPin_when204_thenPinHashedAndStored() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        UserEntity refreshed = userRepository.findById(manager.getId()).orElseThrow();
        assertThat(refreshed.getPinHash()).isNotBlank();
        assertThat(refreshed.getPinHash()).isNotEqualTo("1234");
        assertThat(passwordEncoder.matches("1234", refreshed.getPinHash())).isTrue();
    }

    @Test
    void givenCorrectPin_whenVerify_then200WithScopedOverrideToken() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/pin/verify")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinVerifyRequest("1234", OverrideActionType.DISCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideToken").isString())
                .andExpect(jsonPath("$.actionType").value("DISCOUNT"));
    }

    @Test
    void givenWrongPin_whenVerify_then401AndFailCountIncremented() throws Exception {
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

        assertThat(userRepository.findById(manager.getId()).orElseThrow().getPinFailCount()).isEqualTo((short) 1);
    }

    @Test
    void givenFiveWrongPins_whenVerify_then423AndAccountLocked() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        for (int i = 1; i <= 5; i++) {
            int expected = (i == 5) ? 423 : 401;
            mockMvc.perform(post("/auth/pin/verify")
                            .header("Authorization", "Bearer " + accessToken(manager))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PinVerifyRequest("9999", OverrideActionType.DISCOUNT))))
                    .andExpect(status().is(expected));
        }
    }

    @Test
    void givenLockedPin_whenVerify_then423WithLockedUntilTimestamp() throws Exception {
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

        mockMvc.perform(post("/auth/pin/verify")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinVerifyRequest("1234", OverrideActionType.DISCOUNT))))
                .andExpect(status().isLocked())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("locked"));
    }

    @Test
    void givenDiscountOverrideToken_whenUsedForVoidAction_then403() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        UserEntity cashier = createUser("Cashier", "+233200000004", "cashier@x.com", "secret123", Role.CASHIER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(post("/auth/pin/verify")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinVerifyRequest("1234", OverrideActionType.DISCOUNT))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("overrideToken").asText();

        mockMvc.perform(post("/financial/void")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrideToken\":\"" + token + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenOverrideToken_whenUsedAfter5Minutes_then401Expired() throws Exception {
        UserEntity manager = createUser("Manager", "+233200000003", "manager@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());
        String body = mockMvc.perform(post("/auth/pin/verify")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinVerifyRequest("1234", OverrideActionType.DISCOUNT))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("overrideToken").asText();
        Thread.sleep(1200);

        mockMvc.perform(post("/financial/discount")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrideToken\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenNonManagerRole_whenSetPin_then403() throws Exception {
        UserEntity customer = createUser("Customer", "+233200000005", "customer@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(post("/users/" + customer.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }
}
