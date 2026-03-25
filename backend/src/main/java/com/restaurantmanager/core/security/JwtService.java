package com.restaurantmanager.core.security;

import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.common.TokenType;
import com.restaurantmanager.core.config.SecurityProps;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecurityProps securityProps;
    private final SecretKey key;

    public JwtService(SecurityProps securityProps) {
        this.securityProps = securityProps;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(toBase64(securityProps.getJwt().getSecret())));
    }

    public String generateAccessToken(UUID userId, Role role) {
        return generateToken(userId, role, null, TokenType.ACCESS, null,
                Duration.ofSeconds(securityProps.getJwt().getAccessTtlSeconds()));
    }

    public String generateRefreshToken(UUID userId, Role role) {
        return generateToken(userId, role, null, TokenType.REFRESH, null,
                Duration.ofSeconds(securityProps.getJwt().getRefreshTtlSeconds()));
    }

    public String generateAccessToken(UUID userId, Role role, UUID branchId) {
        return generateToken(userId, role, branchId, TokenType.ACCESS, null,
                Duration.ofSeconds(securityProps.getJwt().getAccessTtlSeconds()));
    }

    public String generateRefreshToken(UUID userId, Role role, UUID branchId) {
        return generateToken(userId, role, branchId, TokenType.REFRESH, null,
                Duration.ofSeconds(securityProps.getJwt().getRefreshTtlSeconds()));
    }

    public String generateOverrideToken(UUID userId, Role role, OverrideActionType actionType) {
        return generateToken(userId, role, null, TokenType.OVERRIDE, actionType,
                Duration.ofSeconds(securityProps.getJwt().getOverrideTtlSeconds()));
    }

    public String generateRefreshTokenWithTtl(UUID userId, Role role, Duration ttl) {
        return generateToken(userId, role, null, TokenType.REFRESH, null, ttl);
    }

    public String generateAccessTokenWithTtl(UUID userId, Role role, Duration ttl) {
        return generateToken(userId, role, null, TokenType.ACCESS, null, ttl);
    }

    public String generateOverrideTokenWithTtl(UUID userId, Role role, OverrideActionType actionType, Duration ttl) {
        return generateToken(userId, role, null, TokenType.OVERRIDE, actionType, ttl);
    }

    private String generateToken(UUID userId, Role role, UUID branchId, TokenType tokenType, OverrideActionType actionType, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .claim("branchId", branchId == null ? null : branchId.toString())
                .claim("tokenType", tokenType.name())
                .claim("actionType", actionType == null ? null : actionType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID userId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public Role role(Claims claims) {
        return Role.valueOf(claims.get("role", String.class));
    }

    public UUID branchId(Claims claims) {
        String value = claims.get("branchId", String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public TokenType tokenType(Claims claims) {
        return TokenType.valueOf(claims.get("tokenType", String.class));
    }

    public OverrideActionType actionType(Claims claims) {
        String value = claims.get("actionType", String.class);
        return value == null ? null : OverrideActionType.valueOf(value);
    }

    public Instant expiration(Claims claims) {
        return claims.getExpiration().toInstant();
    }

    private static String toBase64(String raw) {
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes());
    }
}
