package com.restaurantmanager.core.auth;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.auth.dto.LoginRequest;
import com.restaurantmanager.core.auth.dto.LogoutRequest;
import com.restaurantmanager.core.auth.dto.RefreshRequest;
import com.restaurantmanager.core.auth.dto.RegisterRequest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void givenValidData_whenRegister_then201AndUserCreatedWithCustomerRole() throws Exception {
        var req = new RegisterRequest("Kofi Mensah", "+233201234567", "kofi@example.com", "secret123");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void givenDuplicatePhone_whenRegister_then409() throws Exception {
        createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        var req = new RegisterRequest("Ama", "+233201234567", "ama@example.com", "secret123");
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenDuplicateEmail_whenRegister_then409() throws Exception {
        createUser("Kofi", "+233201234500", "dup@example.com", "secret123", Role.CUSTOMER);
        var req = new RegisterRequest("Ama", "+233201234567", "dup@example.com", "secret123");
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenCorrectCredentials_whenLogin_then200WithAccessAndRefreshTokens() throws Exception {
        createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        var req = new LoginRequest("+233201234567", "secret123");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void givenWrongPassword_whenLogin_then401() throws Exception {
        createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        var req = new LoginRequest("+233201234567", "wrongpass");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenTenFailedLogins_whenLoginAgain_then429() throws Exception {
        createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        var req = new LoginRequest("+233201234567", "wrongpass");
        String payload = objectMapper.writeValueAsString(req);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(i == 9 ? status().isTooManyRequests() : status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void givenUnknownPhone_whenLogin_then401() throws Exception {
        var req = new LoginRequest("+233201234567", "secret123");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenValidRefreshToken_whenRefresh_then200WithNewAccessToken() throws Exception {
        createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        String loginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("+233201234567", "secret123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void givenRevokedRefreshToken_whenRefresh_then401() throws Exception {
        UserEntity user = createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        String access = accessToken(user);
        String loginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("+233201234567", "secret123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenExpiredRefreshToken_whenRefresh_then401() throws Exception {
        UserEntity user = createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        String expired = jwtService.generateRefreshTokenWithTtl(user.getId(), user.getRole(), Duration.ofSeconds(-5));
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(user);
        entity.setTokenHash(AuthService.hashToken(expired));
        entity.setExpiresAt(java.time.Instant.now().minusSeconds(5));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(expired))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenNoToken_whenAccessProtectedEndpoint_then401() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void givenExpiredAccessToken_whenAccessProtectedEndpoint_then401() throws Exception {
        UserEntity admin = createUser("Admin", "+233200000001", "admin@x.com", "secret123", Role.ADMIN);
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + expiredAccessToken(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenValidLogout_whenRefreshAfterwards_then401() throws Exception {
        UserEntity user = createUser("Kofi", "+233201234567", "kofi@x.com", "secret123", Role.CUSTOMER);
        String access = accessToken(user);

        String loginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("+233201234567", "secret123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.findByTokenHash(AuthService.hashToken(refreshToken))).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(AuthService.hashToken(refreshToken)).orElseThrow().isRevoked()).isTrue();
    }
}
