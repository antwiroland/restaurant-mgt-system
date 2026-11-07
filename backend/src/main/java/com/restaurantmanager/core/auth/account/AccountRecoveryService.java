package com.restaurantmanager.core.auth.account;

import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.auth.RefreshTokenRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.notification.email.EmailGateway;
import com.restaurantmanager.core.notification.sms.SmsGateway;
import com.restaurantmanager.core.security.SecurityGuardService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Password reset (by email link, or SMS link when the account has no email), password change, and email
 * verification. Tokens are random, single-use, short-lived and stored only as SHA-256 hashes.
 */
@Service
public class AccountRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final Duration VERIFY_TTL = Duration.ofHours(48);

    private final AccountTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailGateway emailGateway;
    private final SmsGateway smsGateway;
    private final AuditService auditService;
    private final SecurityGuardService securityGuardService;
    private final String publicWebUrl;
    private final String venueName;

    public AccountRecoveryService(AccountTokenRepository tokenRepository,
                                  UserRepository userRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  PasswordEncoder passwordEncoder,
                                  EmailGateway emailGateway,
                                  SmsGateway smsGateway,
                                  AuditService auditService,
                                  SecurityGuardService securityGuardService,
                                  @Value("${app.public-web-url:http://localhost:3000}") String publicWebUrl,
                                  @Value("${app.venue-name:Restaurant}") String venueName) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailGateway = emailGateway;
        this.smsGateway = smsGateway;
        this.auditService = auditService;
        this.securityGuardService = securityGuardService;
        this.publicWebUrl = publicWebUrl.replaceAll("/+$", "");
        this.venueName = venueName;
    }

    /** Always succeeds from the caller's point of view so account existence is never revealed. */
    @Transactional
    public void requestReset(String emailOrPhone, String ipAddress) {
        String identifier = emailOrPhone == null ? "" : emailOrPhone.trim();
        Optional<UserEntity> user = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier)
                : userRepository.findByPhone(identifier);
        user.filter(UserEntity::isActive).ifPresentOrElse(
                u -> sendReset(u, ipAddress, null),
                () -> log.info("Password reset requested for unknown account"));
    }

    /** Admin-initiated reset for a staff member. */
    @Transactional
    public void adminReset(UUID userId, UserPrincipal admin, String ipAddress) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ApiException(404, "User not found"));
        if (user.getEmail() == null && user.getPhone() == null) {
            throw new ApiException(400, "User has no email or phone to send a reset link to");
        }
        sendReset(user, ipAddress, admin.userId());
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String ipAddress) {
        AccountTokenEntity record = consume(token, AccountTokenEntity.Purpose.PASSWORD_RESET);
        UserEntity user = userRepository.findById(record.getUserId()).orElseThrow(() -> new ApiException(400, "Invalid or expired link"));
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId());
        securityGuardService.clearLoginFailures(user.getPhone());
        auditService.log(user, AuditAction.PASSWORD_RESET, "User", user.getId(), "{\"sessionsRevoked\":true}", ipAddress);
    }

    @Transactional
    public void changePassword(UserPrincipal principal, String currentPassword, String newPassword, String ipAddress) {
        UserEntity user = userRepository.findById(principal.userId()).orElseThrow(() -> new ApiException(401, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(400, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(400, "New password must be different from the current one");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId());
        auditService.log(user, AuditAction.PASSWORD_CHANGED, "User", user.getId(), "{\"sessionsRevoked\":true}", ipAddress);
    }

    @Transactional
    public void requestEmailVerification(UserPrincipal principal) {
        UserEntity user = userRepository.findById(principal.userId()).orElseThrow(() -> new ApiException(401, "User not found"));
        sendVerification(user);
    }

    /** Sends a verification link when the user has an unverified email (best effort; never fails the caller). */
    @Transactional
    public void sendVerificationIfNeeded(UserEntity user) {
        if (user.getEmail() != null && user.getEmailVerifiedAt() == null) {
            try {
                sendVerification(user);
            } catch (RuntimeException ex) {
                log.warn("Could not send verification email: {}", ex.getMessage());
            }
        }
    }

    @Transactional
    public void verifyEmail(String token, String ipAddress) {
        AccountTokenEntity record = consume(token, AccountTokenEntity.Purpose.EMAIL_VERIFICATION);
        UserEntity user = userRepository.findById(record.getUserId()).orElseThrow(() -> new ApiException(400, "Invalid or expired link"));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(record.getEmail())) {
            throw new ApiException(400, "This link is for an email address that is no longer on the account");
        }
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        auditService.log(user, AuditAction.EMAIL_VERIFIED, "User", user.getId(), null, ipAddress);
    }

    // ------------------------------------------------------------ internals

    private void sendReset(UserEntity user, String ipAddress, UUID requestedBy) {
        tokenRepository.invalidateOpen(user.getId(), AccountTokenEntity.Purpose.PASSWORD_RESET, Instant.now());
        String token = issue(user, AccountTokenEntity.Purpose.PASSWORD_RESET, RESET_TTL, null);
        String link = publicWebUrl + "/reset-password?token=" + token;
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailGateway.send(user.getEmail(), venueName + ": reset your password", """
                    Hello %s,

                    We received a request to reset your password. Open this link within 30 minutes to choose a new one:

                    %s

                    If you did not ask for this, you can ignore this email; your password stays the same.
                    """.formatted(user.getName(), link));
        } else {
            smsGateway.send(user.getPhone(), venueName + ": reset your password within 30 minutes: " + link);
        }
        auditService.log(user, AuditAction.PASSWORD_RESET_REQUESTED, "User", user.getId(),
                requestedBy == null ? "{\"by\":\"self\"}" : "{\"by\":\"" + requestedBy + "\"}", ipAddress);
    }

    private void sendVerification(UserEntity user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ApiException(400, "Add an email address to your account first");
        }
        if (user.getEmailVerifiedAt() != null) {
            throw new ApiException(409, "Email is already verified");
        }
        tokenRepository.invalidateOpen(user.getId(), AccountTokenEntity.Purpose.EMAIL_VERIFICATION, Instant.now());
        String token = issue(user, AccountTokenEntity.Purpose.EMAIL_VERIFICATION, VERIFY_TTL, user.getEmail());
        emailGateway.send(user.getEmail(), venueName + ": confirm your email", """
                Hello %s,

                Please confirm this email address for your account:

                %s
                """.formatted(user.getName(), publicWebUrl + "/verify-email?token=" + token));
    }

    private String issue(UserEntity user, AccountTokenEntity.Purpose purpose, Duration ttl, String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AccountTokenEntity record = new AccountTokenEntity();
        record.setUserId(user.getId());
        record.setPurpose(purpose);
        record.setTokenHash(hash(token));
        record.setEmail(email);
        record.setExpiresAt(Instant.now().plus(ttl));
        tokenRepository.save(record);
        return token;
    }

    private AccountTokenEntity consume(String token, AccountTokenEntity.Purpose purpose) {
        if (token == null || token.isBlank()) {
            throw new ApiException(400, "Invalid or expired link");
        }
        AccountTokenEntity record = tokenRepository.findByTokenHash(hash(token.trim()))
                .filter(t -> t.getPurpose() == purpose)
                .filter(t -> t.usable(Instant.now()))
                .orElseThrow(() -> new ApiException(400, "Invalid or expired link"));
        record.setUsedAt(Instant.now());
        tokenRepository.save(record);
        return record;
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ApiException(400, "Password must be at least 8 characters");
        }
        if (!password.chars().anyMatch(Character::isLetter) || !password.chars().anyMatch(Character::isDigit)) {
            throw new ApiException(400, "Password must contain letters and numbers");
        }
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
