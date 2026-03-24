package com.restaurantmanager.core.auth;

import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.auth.dto.*;
import com.restaurantmanager.core.common.*;
import com.restaurantmanager.core.config.SecurityProps;
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

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityProps securityProps,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProps = securityProps;
        this.auditService = auditService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new ApiException(409, "Phone already exists");
        }
        if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email())) {
            throw new ApiException(409, "Email already exists");
        }
        UserEntity user = new UserEntity();
        user.setName(request.name());
        user.setPhone(request.phone());
        user.setEmail((request.email() == null || request.email().isBlank()) ? null : request.email());
        user.setRole(Role.CUSTOMER);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        UserEntity saved = userRepository.save(user);

        String access = jwtService.generateAccessToken(saved.getId(), saved.getRole());
        String refresh = jwtService.generateRefreshToken(saved.getId(), saved.getRole());
        storeRefresh(saved, refresh);

        return new RegisterResponse(saved.getId(), saved.getName(), saved.getPhone(), saved.getRole().name(), access, refresh);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        UserEntity user = userRepository.findByPhone(request.phone())
                .orElseThrow(() -> new ApiException(401, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(401, "Invalid credentials");
        }

        String access = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getRole());
        storeRefresh(user, refresh);

        auditService.log(user, AuditAction.USER_LOGIN, "User", user.getId(), "{\"result\":\"SUCCESS\"}", ipAddress);
        return new AuthResponse(access, refresh, securityProps.getJwt().getAccessTtlSeconds(),
                new UserView(user.getId(), user.getName(), user.getRole().name()));
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
        String access = jwtService.generateAccessToken(user.getId(), user.getRole());
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
