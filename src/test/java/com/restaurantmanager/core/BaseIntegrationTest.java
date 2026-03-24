package com.restaurantmanager.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.audit.AuditLogRepository;
import com.restaurantmanager.core.auth.RefreshTokenRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.JwtService;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("security.jwt.secret", () -> "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        registry.add("security.jwt.access-ttl-seconds", () -> "900");
        registry.add("security.jwt.refresh-ttl-seconds", () -> "1209600");
        registry.add("security.jwt.override-ttl-seconds", () -> "1");
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanupDb() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected UserEntity createUser(String name, String phone, String email, String rawPassword, Role role) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    protected String accessToken(UserEntity user) {
        return jwtService.generateAccessToken(user.getId(), user.getRole());
    }

    protected String expiredAccessToken(UserEntity user) {
        return jwtService.generateAccessTokenWithTtl(user.getId(), user.getRole(), Duration.ofSeconds(-5));
    }
}
