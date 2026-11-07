package com.restaurantmanager.core.auth;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.notification.email.EmailGateway;
import com.restaurantmanager.core.notification.sms.SmsGateway;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountRecoveryIntegrationTest extends BaseIntegrationTest {
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @MockBean
    private EmailGateway emailGateway;
    @MockBean
    private SmsGateway smsGateway;

    private UserEntity cashier;

    @BeforeEach
    void setUpData() {
        reset(emailGateway, smsGateway);
        cashier = createUser("Esi", "+233360000001", "esi.cashier@x.com", "oldpass123", Role.CASHIER);
    }

    @Test
    void givenForgottenPassword_whenResetViaEmailLink_thenNewPasswordWorksAndOldSessionsRevoked() throws Exception {
        String refresh = objectMapper.readTree(login("+233360000001", "oldpass123")).get("refreshToken").asText();

        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"ESI.cashier@x.com\"}"))
                .andExpect(status().isAccepted());
        String token = tokenFromEmail("esi.cashier@x.com");

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isNoContent());

        // single use
        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"another789\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+233360000001\",\"password\":\"oldpass123\"}"))
                .andExpect(status().isUnauthorized());
        login("+233360000001", "newpass456");
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenUnknownAccount_whenResetRequested_thenSameResponseAndNothingSent() throws Exception {
        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"nobody@x.com\"}"))
                .andExpect(status().isAccepted());
        verify(emailGateway, never()).send(anyString(), anyString(), anyString());
        verify(smsGateway, never()).send(anyString(), anyString());
    }

    @Test
    void givenAccountWithoutEmail_whenResetRequestedByPhone_thenLinkSentBySms() throws Exception {
        createUser("Kojo", "+233360000002", null, "oldpass123", Role.CASHIER);
        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"+233360000002\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> sms = ArgumentCaptor.forClass(String.class);
        verify(smsGateway).send(eq("+233360000002"), sms.capture());
        assertTrue(sms.getValue().contains("/reset-password?token="));
    }

    @Test
    void givenSignedInUser_whenChangingPassword_thenCurrentPasswordRequired() throws Exception {
        mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isNoContent());
        login("+233360000001", "newpass456");
    }

    @Test
    void givenUnverifiedEmail_whenVerificationLinkUsed_thenEmailMarkedVerified() throws Exception {
        mockMvc.perform(post("/auth/email/verification")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isAccepted());
        String token = tokenFromEmail("esi.cashier@x.com");

        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        assertNotNull(userRepository.findById(cashier.getId()).orElseThrow().getEmailVerifiedAt());

        mockMvc.perform(post("/auth/email/verification")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenAdmin_whenTriggeringStaffReset_thenLinkSent_andNonAdminForbidden() throws Exception {
        UserEntity admin = createUser("Admin", "+233360000003", "admin@x.com", "adminpass1", Role.ADMIN);
        mockMvc.perform(post("/users/" + cashier.getId() + "/password-reset")
                        .header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isAccepted());
        tokenFromEmail("esi.cashier@x.com");

        mockMvc.perform(post("/users/" + admin.getId() + "/password-reset")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    private String login(String phone, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String tokenFromEmail(String to) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailGateway, org.mockito.Mockito.atLeastOnce()).send(eq(to), anyString(), body.capture());
        Matcher matcher = TOKEN.matcher(body.getValue());
        assertTrue(matcher.find(), body.getValue());
        return matcher.group(1);
    }
}
