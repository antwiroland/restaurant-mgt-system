package com.restaurantmanager.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.audit.AuditLogRepository;
import com.restaurantmanager.core.auth.RefreshTokenRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryRepository;
import com.restaurantmanager.core.menu.MenuModifierGroupRepository;
import com.restaurantmanager.core.menu.MenuModifierOptionRepository;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.order.GroupOrderSessionRepository;
import com.restaurantmanager.core.order.GroupSessionItemRepository;
import com.restaurantmanager.core.order.GroupSessionParticipantRepository;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentWebhookEventRepository;
import com.restaurantmanager.core.phase8.promo.PromoCodeRepository;
import com.restaurantmanager.core.phase10.security.SecurityGuardService;
import com.restaurantmanager.core.reservation.ReservationRepository;
import com.restaurantmanager.core.security.JwtService;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

@Tag("postgres")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BasePostgresIntegrationTest {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("restaurant_manager")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("security.jwt.secret", () -> "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        registry.add("security.jwt.access-ttl-seconds", () -> "900");
        registry.add("security.jwt.refresh-ttl-seconds", () -> "1209600");
        registry.add("security.jwt.override-ttl-seconds", () -> "5");
        registry.add("payments.paystack.secret-key", () -> "test_paystack_secret");
        registry.add("payments.reconciliation.enabled", () -> "false");
        registry.add("app.rate-limit.enabled", () -> "false");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
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
    @Autowired
    protected ReservationRepository reservationRepository;
    @Autowired
    protected RestaurantTableRepository restaurantTableRepository;
    @Autowired
    protected MenuItemRepository menuItemRepository;
    @Autowired
    protected CategoryRepository categoryRepository;
    @Autowired
    protected MenuModifierGroupRepository menuModifierGroupRepository;
    @Autowired
    protected MenuModifierOptionRepository menuModifierOptionRepository;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected OrderItemRepository orderItemRepository;
    @Autowired
    protected GroupOrderSessionRepository groupOrderSessionRepository;
    @Autowired
    protected GroupSessionParticipantRepository groupSessionParticipantRepository;
    @Autowired
    protected GroupSessionItemRepository groupSessionItemRepository;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Autowired
    protected PromoCodeRepository promoCodeRepository;
    @Autowired
    protected SecurityGuardService securityGuardService;

    @BeforeEach
    void cleanupDb() {
        paymentWebhookEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        groupSessionItemRepository.deleteAll();
        groupSessionParticipantRepository.deleteAll();
        groupOrderSessionRepository.deleteAll();
        reservationRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        menuModifierOptionRepository.deleteAll();
        menuModifierGroupRepository.deleteAll();
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
        promoCodeRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        securityGuardService.clearAllLoginFailures();
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
