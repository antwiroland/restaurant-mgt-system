package com.restaurantmanager.core.auth;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.auth.otp.OtpChallengeEntity;
import com.restaurantmanager.core.auth.otp.OtpPurpose;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.notification.sms.SmsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import com.restaurantmanager.core.auth.otp.OtpProps;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OtpIntegrationTest extends BaseIntegrationTest {
    private static final Pattern CODE = Pattern.compile("code is (\\d{6})");
    private static final String PHONE = "+233270000001";

    @MockBean
    private SmsGateway smsGateway;

    @Autowired
    private OtpProps otpProps;

    @BeforeEach
    void resetSms() {
        reset(smsGateway);
        otpProps.setRequiredForRegistration(true);
    }

    @AfterEach
    void restoreDefaults() {
        otpProps.setRequiredForRegistration(false);
    }

    @Test
    void givenVerifiedPhone_whenRegistering_thenAccountCreatedAndPhoneMarkedVerified() throws Exception {
        String code = requestCode(PHONE, "REGISTER");

        String verifyBody = mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andReturn().getResponse().getContentAsString();
        String verificationToken = objectMapper.readTree(verifyBody).get("verificationToken").asText();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Abena\",\"phone\":\"" + PHONE + "\",\"password\":\"secret123\",\"verificationToken\":\"" + verificationToken + "\"}"))
                .andExpect(status().isCreated());

        assertNotNull(userRepository.findByPhone(PHONE).orElseThrow().getPhoneVerifiedAt());
    }

    @Test
    void givenNoVerification_whenRegistering_then400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Abena\",\"phone\":\"" + PHONE + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenTokenForOtherPhone_whenRegistering_then400() throws Exception {
        String code = requestCode(PHONE, "REGISTER");
        String verifyBody = mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(verifyBody).get("verificationToken").asText();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"phone\":\"+233270000099\",\"password\":\"secret123\",\"verificationToken\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenWrongCode_whenVerifiedRepeatedly_thenLockedAfterMaxAttempts() throws Exception {
        requestCode(PHONE, "REGISTER");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + PHONE + "\",\"code\":\"000000\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"000000\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void givenCodeAlreadyUsed_whenVerifiedAgain_then400() throws Exception {
        String code = requestCode(PHONE, "REGISTER");
        String body = "{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenExpiredCode_whenVerified_then400() throws Exception {
        String code = requestCode(PHONE, "REGISTER");
        OtpChallengeEntity challenge = otpChallengeRepository.findFirstByPhoneOrderByCreatedAtDesc(PHONE).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));
        otpChallengeRepository.save(challenge);

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenRecentRequest_whenRequestingAgain_then429Cooldown() throws Exception {
        requestCode(PHONE, "REGISTER");
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void givenRegisteredPhone_whenRequestingRegisterCode_then409() throws Exception {
        createUser("Kwame", PHONE, "kwame@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenExistingCustomer_whenLoggingInWithOtp_thenTokensIssued() throws Exception {
        createUser("Kwame", PHONE, "kwame@x.com", "secret123", Role.CUSTOMER);
        String code = requestCode(PHONE, "LOGIN");

        mockMvc.perform(post("/auth/otp/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void givenUnknownPhone_whenRequestingLoginCode_thenNoSmsSentButResponseIdentical() throws Exception {
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
        verify(smsGateway, never()).send(anyString(), anyString());
    }

    @Test
    void givenSmsProviderDown_whenRequestingCode_then502AndNoChallengeStored() throws Exception {
        doThrow(new RuntimeException("provider down")).when(smsGateway).send(anyString(), anyString());
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isBadGateway());
        assertEquals(0, otpChallengeRepository.count());
    }

    private String requestCode(String phone, String purpose) throws Exception {
        clearInvocations(smsGateway);
        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsGateway).send(org.mockito.ArgumentMatchers.eq(phone), message.capture());
        Matcher matcher = CODE.matcher(message.getValue());
        assertTrue(matcher.find(), "SMS should contain a 6-digit code: " + message.getValue());
        assertEquals(OtpPurpose.valueOf(purpose),
                otpChallengeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone).orElseThrow().getPurpose());
        return matcher.group(1);
    }
}
