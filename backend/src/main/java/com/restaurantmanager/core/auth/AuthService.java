package com.restaurantmanager.core.auth;

import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.auth.dto.*;
import com.restaurantmanager.core.common.*;
import com.restaurantmanager.core.auth.otp.OtpPurpose;
import com.restaurantmanager.core.auth.otp.OtpService;
import com.restaurantmanager.core.config.SecurityProps;
import com.restaurantmanager.core.security.SecurityGuardService;
import com.restaurantmanager.core.security.JwtService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProps securityProps;
    private final AuditService auditService;
    private final SecurityGuardService securityGuardService;
    private final OtpService otpService;
    private final com.restaurantmanager.core.auth.account.AccountRecoveryService accountRecoveryService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityProps securityProps,
                       AuditService auditService,
                       SecurityGuardService securityGuardService,
                       OtpService otpService,
                       @org.springframework.context.annotation.Lazy com.restaurantmanager.core.auth.account.AccountRecoveryService accountRecoveryService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProps = securityProps;
        this.auditService = auditService;
        this.securityGuardService = securityGuardService;
        this.otpService = otpService;
        this.accountRecoveryService = accountRecoveryService;
    }

    /** Verifies a registration OTP and returns a short-lived token proving ownership of the phone. */
    public PhoneVerificationResponse verifyPhoneForRegistration(OtpVerifyRequest request) {
        String phone = otpService.verify(request.phone(), request.code(), OtpPurpose.REGISTER);
        long ttl = otpService.props().getVerificationTokenTtlSeconds();
        return new PhoneVerificationResponse(phone,
                jwtService.generatePhoneVerificationToken(phone, java.time.Duration.ofSeconds(ttl)), ttl);
    }

    /** Passwordless customer login with an SMS code. */
    @Transactional
    public AuthResponse loginWithOtp(OtpVerifyRequest request, String ipAddress) {
        String phone = otpService.verify(request.phone(), request.code(), OtpPurpose.LOGIN);
        UserEntity user = userRepository.findByPhone(phone)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ApiException(401, "Invalid or expired code"));
        if (user.getPhoneVerifiedAt() == null) {
            user.setPhoneVerifiedAt(Instant.now());
            userRepository.save(user);
        }
        UUID branchId = user.getBranch() == null ? null : user.getBranch().getId();
        String access = jwtService.generateAccessToken(user.getId(), user.getRole(), branchId);
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getRole(), branchId);
        storeRefresh(user, refresh);
        auditService.log(user, AuditAction.USER_LOGIN, "User", user.getId(), "{\"result\":\"SUCCESS\",\"method\":\"OTP\"}", ipAddress);
        return new AuthResponse(access, refresh, securityProps.getJwt().getAccessTtlSeconds(),
                new UserView(user.getId(), user.getName(), user.getRole().name(),
                        user.getBranch() == null ? null : user.getBranch().getId(),
                        user.getBranch() == null ? null : user.getBranch().getName()));
    }

    private boolean phoneVerifiedBy(String verificationToken, String phone) {
        if (verificationToken == null || verificationToken.isBlank()) {
            return false;
        }
        try {
            Claims claims = jwtService.parse(verificationToken);
            return jwtService.tokenType(claims) == TokenType.PHONE_VERIFICATION
                    && OtpService.normalize(phone).equals(jwtService.phone(claims));
        } catch (Exception ex) {
            return false;
        }
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new ApiException(409, "Phone already exists");
        }
        if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email())) {
            throw new ApiException(409, "Email already exists");
        }
        boolean verified = phoneVerifiedBy(request.verificationToken(), request.phone());
        if (otpService.props().isRequiredForRegistration() && !verified) {
            throw new ApiException(400, "Phone verification required. Request and verify an SMS code first");
        }
        UserEntity user = new UserEntity();
        user.setName(request.name());
        user.setPhone(request.phone());
        user.setPhoneVerifiedAt(verified ? Instant.now() : null);
        user.setEmail((request.email() == null || request.email().isBlank()) ? null : request.email());
        user.setRole(Role.CUSTOMER);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        UserEntity saved = userRepository.save(user);
        accountRecoveryService.sendVerificationIfNeeded(saved);

        UUID branchId = saved.getBranch() == null ? null : saved.getBranch().getId();
        String access = jwtService.generateAccessToken(saved.getId(), saved.getRole(), branchId);
        String refresh = jwtService.generateRefreshToken(saved.getId(), saved.getRole(), branchId);
        storeRefresh(saved, refresh);

        return new RegisterResponse(saved.getId(), saved.getName(), saved.getPhone(), saved.getRole().name(), access, refresh);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        String phone = request.phone().trim();
        if (securityGuardService.isLoginRateLimited(phone)) {
            throw new ApiException(429, "Too many failed login attempts");
        }
        UserEntity user = userRepository.findByPhone(phone)
                .orElseThrow(() -> {
                    securityGuardService.registerLoginFailure(phone);
                    if (securityGuardService.isLoginRateLimited(phone)) {
                        return new ApiException(429, "Too many failed login attempts");
                    }
                    return new ApiException(401, "Invalid credentials");
                });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            securityGuardService.registerLoginFailure(phone);
            if (securityGuardService.isLoginRateLimited(phone)) {
                throw new ApiException(429, "Too many failed login attempts");
            }
            throw new ApiException(401, "Invalid credentials");
        }
        securityGuardService.clearLoginFailures(phone);

        UUID branchId = user.getBranch() == null ? null : user.getBranch().getId();
        String access = jwtService.generateAccessToken(user.getId(), user.getRole(), branchId);
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getRole(), branchId);
        storeRefresh(user, refresh);

        auditService.log(user, AuditAction.USER_LOGIN, "User", user.getId(), "{\"result\":\"SUCCESS\"}", ipAddress);
        return new AuthResponse(access, refresh, securityProps.getJwt().getAccessTtlSeconds(),
                new UserView(
                        user.getId(),
                        user.getName(),
                        user.getRole().name(),
                        user.getBranch() == null ? null : user.getBranch().getId(),
                        user.getBranch() == null ? null : user.getBranch().getName()));
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parse(request.refreshToken());
        if (jwtService.tokenType(claims) != TokenType.REFRESH) {
            throw new ApiException(401, "Invalid refresh token");
        }
        String hash = hashToken(request.refreshToken());
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(401, "Refresh token revoked"));
        if (tokenEntity.isRevoked() || tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(401, "Refresh token revoked");
        }
        UserEntity user = tokenEntity.getUser();
        UUID branchId = user.getBranch() == null ? null : user.getBranch().getId();
        String access = jwtService.generateAccessToken(user.getId(), user.getRole(), branchId);
        return new RefreshResponse(access, securityProps.getJwt().getAccessTtlSeconds());
    }

    @Transactional
    public void logout(UserPrincipal principal, LogoutRequest request, String ipAddress) {
        String hash = hashToken(request.refreshToken());
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(401, "Invalid refresh token"));
        if (!tokenEntity.getUser().getId().equals(principal.userId())) {
            throw new ApiException(403, "Cannot revoke another user's token");
        }
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        UserEntity actor = userRepository.findById(principal.userId()).orElse(null);
        auditService.log(actor, AuditAction.USER_LOGOUT, "User", principal.userId(), "{\"result\":\"SUCCESS\"}", ipAddress);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PinVerifyResponse verifyPin(UserPrincipal principal, PinVerifyRequest request, String ipAddress) {
        if (principal.role() != Role.MANAGER) {
            throw new ApiException(403, "Only managers can verify PIN");
        }
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(401, "User not found"));

        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(423, "PIN is locked until " + user.getPinLockedUntil());
        }
        if (user.getPinHash() == null) {
            throw new ApiException(400, "PIN not set");
        }

        if (!passwordEncoder.matches(request.pin(), user.getPinHash())) {
            short updated = (short) (user.getPinFailCount() + 1);
            user.setPinFailCount(updated);
            auditService.log(user, AuditAction.PIN_FAILED, "User", user.getId(), "{\"attempt\":" + updated + "}", ipAddress);
            if (updated >= securityProps.getPin().getMaxFailedAttempts()) {
                user.setPinLockedUntil(Instant.now().plusSeconds(securityProps.getPin().getLockoutMinutes() * 60L));
                user.setPinFailCount((short) 0);
                auditService.log(user, AuditAction.PIN_LOCKED, "User", user.getId(),
                        "{\"lockedUntil\":\"" + user.getPinLockedUntil() + "\"}", ipAddress);
                userRepository.save(user);
                throw new ApiException(423, "PIN is locked until " + user.getPinLockedUntil());
            }
            userRepository.save(user);
            throw new ApiException(401, "Invalid PIN");
        }

        user.setPinFailCount((short) 0);
        user.setPinLockedUntil(null);
        userRepository.save(user);
        auditService.log(user, AuditAction.PIN_VERIFIED, "User", user.getId(),
                "{\"actionType\":\"" + request.actionType().name() + "\"}", ipAddress);

        String overrideToken = jwtService.generateOverrideToken(user.getId(), user.getRole(), request.actionType());
        return new PinVerifyResponse(overrideToken, securityProps.getJwt().getOverrideTtlSeconds(), request.actionType(), null);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void storeRefresh(UserEntity user, String refreshToken) {
        Claims claims = jwtService.parse(refreshToken);
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(user);
        entity.setTokenHash(hashToken(refreshToken));
        entity.setExpiresAt(jwtService.expiration(claims));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);
    }
}
